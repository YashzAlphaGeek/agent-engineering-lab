"""A reference ``ModelClient`` for the Anthropic Messages API, using only the standard library.

The request and response mapping is covered by offline tests, but it has not been run against the live API in this
repository, so verify it with your own key before depending on it. Rate limits and server errors are retried with
exponential backoff, and an unknown ``stop_reason`` is rejected instead of being treated as a normal finish.

The model id is always supplied by the caller. Keep keys in the environment or a secret store.
"""

from __future__ import annotations

import json
import os
import time
import urllib.error
import urllib.request
from collections.abc import Callable, Mapping
from typing import Any

from .model import (
    ContentBlock,
    ModelError,
    ModelRequest,
    ModelResponse,
    Role,
    StopReason,
    Text,
    ToolResult,
    ToolUse,
)

__all__ = ["AnthropicModelClient", "Transport", "build_body", "parse_response", "urllib_transport"]

DEFAULT_ENDPOINT = "https://api.anthropic.com/v1/messages"
API_VERSION = "2023-06-01"

# A transport sends one HTTP POST and returns (status code, response body text).
# It may raise OSError for network failures, which the client retries.
Transport = Callable[[str, Mapping[str, str], bytes, float], tuple[int, str]]


def _block_to_json(block: ContentBlock) -> dict[str, Any]:
    match block:
        case Text(text=text):
            return {"type": "text", "text": text}
        case ToolUse(id=id_, name=name, input=input_):
            return {"type": "tool_use", "id": id_, "name": name, "input": dict(input_)}
        case ToolResult(tool_use_id=tool_use_id, content=content, is_error=is_error):
            data: dict[str, Any] = {"type": "tool_result", "tool_use_id": tool_use_id, "content": content}
            if is_error:
                data["is_error"] = True
            return data
    raise TypeError(f"Unsupported content block: {block!r}")


def build_body(model: str, request: ModelRequest) -> dict[str, Any]:
    """Map a ``ModelRequest`` to the Messages API request body."""
    body: dict[str, Any] = {"model": model, "max_tokens": request.max_tokens}
    if request.system.strip():
        body["system"] = request.system
    body["messages"] = [
        {
            "role": "user" if message.role is Role.USER else "assistant",
            "content": [_block_to_json(block) for block in message.content],
        }
        for message in request.messages
    ]
    if request.tools:
        body["tools"] = [
            {
                "name": tool.name,
                "description": tool.description,
                "input_schema": tool.input_schema.to_json_schema(),
            }
            for tool in request.tools
        ]
    return body


def parse_response(payload: "str | Mapping[str, Any]") -> ModelResponse:
    """Map a Messages API response to a ``ModelResponse``. Unknown stop reasons raise ``ModelError``."""
    if isinstance(payload, str):
        try:
            root = json.loads(payload)
        except json.JSONDecodeError as error:
            raise ModelError(f"Response was not valid JSON: {error}") from error
    else:
        root = payload
    if not isinstance(root, dict):
        raise ModelError("Response was not a JSON object")
    raw_content = root.get("content")
    if not isinstance(raw_content, list):
        raise ModelError("Response has no content array")

    blocks: list[ContentBlock] = []
    for item in raw_content:
        if not isinstance(item, dict):
            continue
        match item.get("type"):
            case "text":
                blocks.append(Text(str(item.get("text"))))
            case "tool_use":
                raw_input = item.get("input")
                blocks.append(
                    ToolUse(
                        str(item.get("id")),
                        str(item.get("name")),
                        raw_input if isinstance(raw_input, dict) else {},
                    )
                )
            case _:
                pass  # other block types are not used by this library
    return ModelResponse(tuple(blocks), StopReason.from_api(root.get("stop_reason")))


def urllib_transport(url: str, headers: Mapping[str, str], body: bytes, timeout: float) -> tuple[int, str]:
    """The default transport, built on ``urllib.request``."""
    request = urllib.request.Request(url, data=body, headers=dict(headers), method="POST")
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:  # noqa: S310 - https endpoint
            return response.status, response.read().decode("utf-8")
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode("utf-8", "replace")


def _abbreviate(text: str) -> str:
    return text if len(text) <= 300 else text[:300] + "..."


class AnthropicModelClient:
    def __init__(
        self,
        api_key: str,
        model: str,
        *,
        endpoint: str = DEFAULT_ENDPOINT,
        timeout: float = 60.0,
        max_retries: int = 2,
        transport: Transport | None = None,
        sleep: Callable[[float], None] = time.sleep,
    ) -> None:
        if not api_key or not api_key.strip():
            raise ValueError("api_key is required")
        if not model or not model.strip():
            raise ValueError("model is required")
        self._api_key = api_key
        self._model = model
        self._endpoint = endpoint
        self._timeout = timeout
        self._max_retries = max_retries
        self._transport = transport or urllib_transport
        self._sleep = sleep

    @classmethod
    def from_environment(cls, model: str, **options: Any) -> "AnthropicModelClient":
        """Read the key from the ``ANTHROPIC_API_KEY`` environment variable."""
        key = os.environ.get("ANTHROPIC_API_KEY")
        if not key or not key.strip():
            raise RuntimeError("ANTHROPIC_API_KEY is not set")
        return cls(key, model, **options)

    def send(self, request: ModelRequest) -> ModelResponse:
        body = json.dumps(build_body(self._model, request)).encode("utf-8")
        headers = {
            "content-type": "application/json",
            "x-api-key": self._api_key,
            "anthropic-version": API_VERSION,
        }

        last: ModelError | None = None
        for attempt in range(self._max_retries + 1):
            if attempt > 0:
                self._sleep(0.5 * (2 ** (attempt - 1)))
            try:
                status, text = self._transport(self._endpoint, headers, body, self._timeout)
            except OSError as error:  # includes URLError and timeouts
                last = ModelError(f"Network error: {error}")
                continue
            if status == 200:
                return parse_response(text)
            detail = f"HTTP {status}: {_abbreviate(text)}"
            if status == 429 or status >= 500:
                last = ModelError(detail)
                continue
            raise ModelError(detail)
        raise last or ModelError("No attempts were made")
