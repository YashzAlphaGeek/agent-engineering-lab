//! An `Agent` holds no run state, so one instance can be shared and run from many threads at once.

use std::sync::Arc;
use std::thread;

use golden_agent::prelude::*;

fn assert_send_sync<T: Send + Sync>() {}

#[test]
fn the_core_types_are_send_and_sync() {
    assert_send_sync::<Agent>();
    assert_send_sync::<AgentLoop>();
    assert_send_sync::<ToolRegistry>();
    assert_send_sync::<Gate>();
    assert_send_sync::<HumanApprover>();
    assert_send_sync::<ResultFilter>();
    assert_send_sync::<ScriptedModelClient>();
    assert_send_sync::<Arc<dyn Tool>>();
    assert_send_sync::<Arc<dyn ModelClient>>();
}

#[test]
fn one_agent_can_run_concurrently_with_isolated_state() {
    // A model that echoes the goal back, so every run proves it saw only its own goal.
    let echo_goal = |request: &ModelRequest| -> Result<ModelResponse, ModelError> {
        let goal = match &request.messages[0].content[0] {
            ContentBlock::Text(t) => t.clone(),
            _ => String::new(),
        };
        Ok(ScriptedModelClient::end_turn(&format!("answer to {goal}")))
    };
    let agent = Arc::new(
        Agent::builder("shared")
            .model(echo_goal)
            .instructions(Instructions::for_role("test"))
            .build()
            .unwrap(),
    );

    let handles: Vec<_> = (0..16)
        .map(|n| {
            let agent = Arc::clone(&agent);
            thread::spawn(move || {
                let result = agent.run(&format!("goal {n}"));
                (n, result)
            })
        })
        .collect();

    for handle in handles {
        let (n, result) = handle.join().unwrap();
        assert!(result.completed());
        assert_eq!(result.text, format!("answer to goal {n}"));
        assert_eq!(result.history.len(), 2);
    }
}
