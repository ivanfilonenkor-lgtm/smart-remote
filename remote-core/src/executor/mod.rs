#[cfg(target_os = "windows")]
mod windows_input;

use std::sync::Arc;

use anyhow::Result;

use crate::action::Action;

pub trait InputExecutor: Send + Sync + 'static {
    fn execute(&self, action: &Action) -> Result<()>;
}

#[cfg(target_os = "windows")]
pub use windows_input::WindowsInputExecutor;

#[cfg(target_os = "windows")]
pub fn platform_executor() -> Arc<dyn InputExecutor> {
    Arc::new(WindowsInputExecutor::default())
}

#[cfg(not(target_os = "windows"))]
pub fn platform_executor() -> Arc<dyn InputExecutor> {
    Arc::new(UnsupportedExecutor)
}

#[cfg(not(target_os = "windows"))]
struct UnsupportedExecutor;

#[cfg(not(target_os = "windows"))]
impl InputExecutor for UnsupportedExecutor {
    fn execute(&self, _action: &Action) -> Result<()> {
        anyhow::bail!("RemoteCore native actions are supported only on Windows")
    }
}

#[cfg(test)]
#[derive(Default)]
pub struct RecordingExecutor {
    pub actions: parking_lot::Mutex<Vec<Action>>,
}

#[cfg(test)]
impl InputExecutor for RecordingExecutor {
    fn execute(&self, action: &Action) -> Result<()> {
        self.actions.lock().push(action.clone());
        Ok(())
    }
}
