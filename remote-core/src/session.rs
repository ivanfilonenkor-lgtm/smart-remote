use std::collections::{HashMap, HashSet};

use uuid::Uuid;

use crate::action::MouseButton;

#[derive(Debug, Default)]
pub struct ButtonOwnership {
    by_session: HashMap<Uuid, HashSet<MouseButton>>,
    owner_counts: HashMap<MouseButton, usize>,
}

impl ButtonOwnership {
    /// Returns true only when the native button must transition to down.
    pub fn press(&mut self, session: Uuid, button: MouseButton) -> bool {
        let buttons = self.by_session.entry(session).or_default();
        if !buttons.insert(button) {
            return false;
        }
        let count = self.owner_counts.entry(button).or_default();
        *count += 1;
        *count == 1
    }

    /// Returns true only when the native button must transition to up.
    pub fn release(&mut self, session: Uuid, button: MouseButton) -> bool {
        let Some(buttons) = self.by_session.get_mut(&session) else {
            return false;
        };
        if !buttons.remove(&button) {
            return false;
        }
        if buttons.is_empty() {
            self.by_session.remove(&session);
        }
        self.decrement(button)
    }

    /// Removes a session and returns buttons that must physically transition to up.
    pub fn release_session(&mut self, session: Uuid) -> Vec<MouseButton> {
        let Some(buttons) = self.by_session.remove(&session) else {
            return Vec::new();
        };
        buttons
            .into_iter()
            .filter(|button| self.decrement(*button))
            .collect()
    }

    pub fn release_all(&mut self) -> Vec<MouseButton> {
        self.by_session.clear();
        self.owner_counts
            .drain()
            .filter_map(|(button, count)| (count > 0).then_some(button))
            .collect()
    }

    fn decrement(&mut self, button: MouseButton) -> bool {
        let Some(count) = self.owner_counts.get_mut(&button) else {
            return false;
        };
        *count = count.saturating_sub(1);
        if *count == 0 {
            self.owner_counts.remove(&button);
            true
        } else {
            false
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn multiple_sessions_share_native_button() {
        let a = Uuid::new_v4();
        let b = Uuid::new_v4();
        let mut ownership = ButtonOwnership::default();
        assert!(ownership.press(a, MouseButton::Left));
        assert!(!ownership.press(a, MouseButton::Left));
        assert!(!ownership.press(b, MouseButton::Left));
        assert!(!ownership.release(a, MouseButton::Left));
        assert_eq!(ownership.release_session(a), Vec::new());
        assert_eq!(ownership.release_session(b), vec![MouseButton::Left]);
    }

    #[test]
    fn disconnect_releases_only_uniquely_owned_buttons() {
        let a = Uuid::new_v4();
        let b = Uuid::new_v4();
        let mut ownership = ButtonOwnership::default();
        ownership.press(a, MouseButton::Left);
        ownership.press(a, MouseButton::Right);
        ownership.press(b, MouseButton::Left);
        assert_eq!(ownership.release_session(a), vec![MouseButton::Right]);
        assert_eq!(ownership.release_session(b), vec![MouseButton::Left]);
    }
}
