use std::{
    collections::HashMap,
    sync::{
        Arc,
        atomic::{AtomicBool, AtomicU64, Ordering},
    },
    time::{Duration, Instant},
};

use anyhow::{Context, Result};
use axum::{
    Router,
    extract::{
        ConnectInfo, State, WebSocketUpgrade,
        ws::{CloseFrame, Message, WebSocket, close_code},
    },
    http::StatusCode,
    response::{IntoResponse, Response},
    routing::get,
};
use futures_util::{SinkExt, StreamExt};
use parking_lot::Mutex;
use tokio::{
    net::TcpListener,
    sync::{RwLock, mpsc, watch},
    task::JoinHandle,
    time,
};
use uuid::Uuid;

use crate::{
    PROTOCOL_VERSION,
    action::{Action, ButtonState},
    audio::{AudioHandle, AudioState},
    executor::InputExecutor,
    protocol::{
        AnimeVostState, BrowserMessage, BrowserServerMessage, ClientHello, ClientMessage,
        ControlMessage, RealtimeFrame, RealtimeKind, ServerMessage, browser_command_from_control,
    },
    realtime::RealtimeAccumulator,
    session::ButtonOwnership,
};

const CAPABILITIES: &[&str] = &[
    "pointer",
    "scroll",
    "volume",
    "mute",
    "back",
    "play_pause",
    "show_desktop",
    "keyboard",
    "animevost",
];
const CONTROL_QUEUE_CAPACITY: usize = 128;
const HEARTBEAT_TIMEOUT: Duration = Duration::from_secs(3);

struct ClientConnection {
    device_id: Uuid,
    device_name: String,
    outbound: mpsc::UnboundedSender<Message>,
}

struct ControlJob {
    session_id: Uuid,
    message: ControlMessage,
    outbound: mpsc::UnboundedSender<Message>,
}

struct BrowserConnection {
    connection_id: Uuid,
    outbound: mpsc::UnboundedSender<Message>,
}

pub struct ServerState {
    server_id: Uuid,
    server_name: String,
    executor: Arc<dyn InputExecutor>,
    audio: AudioHandle,
    clients: RwLock<HashMap<Uuid, ClientConnection>>,
    browser: RwLock<Option<BrowserConnection>>,
    anime_state: RwLock<AnimeVostState>,
    buttons: Mutex<ButtonOwnership>,
    control_tx: mpsc::Sender<ControlJob>,
    revision: AtomicU64,
    anime_revision: AtomicU64,
    accepting_actions: AtomicBool,
    shutdown_tx: watch::Sender<bool>,
}

impl ServerState {
    pub fn start(
        server_name: String,
        executor: Arc<dyn InputExecutor>,
        audio: AudioHandle,
    ) -> Arc<Self> {
        let (control_tx, control_rx) = mpsc::channel(CONTROL_QUEUE_CAPACITY);
        let (shutdown_tx, _) = watch::channel(false);
        let state = Arc::new(Self {
            server_id: Uuid::new_v4(),
            server_name,
            executor,
            audio,
            clients: RwLock::new(HashMap::new()),
            browser: RwLock::new(None),
            anime_state: RwLock::new(AnimeVostState::default()),
            buttons: Mutex::new(ButtonOwnership::default()),
            control_tx,
            revision: AtomicU64::new(0),
            anime_revision: AtomicU64::new(0),
            accepting_actions: AtomicBool::new(true),
            shutdown_tx,
        });
        tokio::spawn(control_worker(state.clone(), control_rx));
        tokio::spawn(audio_broadcaster(state.clone()));
        state
    }

    pub fn server_id(&self) -> Uuid {
        self.server_id
    }

    async fn register(
        &self,
        session_id: Uuid,
        hello: &ClientHello,
        outbound: mpsc::UnboundedSender<Message>,
    ) {
        self.clients.write().await.insert(
            session_id,
            ClientConnection {
                device_id: hello.client_id,
                device_name: hello.device_name.clone(),
                outbound,
            },
        );
        let client_count = self.clients.read().await.len();
        tracing::info!(
            %session_id,
            device_id = %hello.client_id,
            device_name = %hello.device_name,
            clients = client_count,
            "client connected"
        );
    }

    async fn unregister(&self, session_id: Uuid, reason: &str) {
        let client = self.clients.write().await.remove(&session_id);
        self.release_session_buttons(session_id);
        if let Some(client) = client {
            tracing::info!(
                %session_id,
                device_id = %client.device_id,
                device_name = %client.device_name,
                %reason,
                "client disconnected"
            );
        }
    }

    async fn register_browser(
        &self,
        connection_id: Uuid,
        outbound: mpsc::UnboundedSender<Message>,
        extension_version: &str,
    ) {
        if let Some(previous) = self.browser.write().await.replace(BrowserConnection {
            connection_id,
            outbound,
        }) {
            let _ = previous.outbound.send(Message::Close(Some(CloseFrame {
                code: close_code::AWAY,
                reason: "new browser bridge connected".into(),
            })));
        }
        tracing::info!(%connection_id, %extension_version, "local video-site browser bridge connected");
    }

    async fn unregister_browser(&self, connection_id: Uuid, reason: &str) {
        let mut browser = self.browser.write().await;
        if browser
            .as_ref()
            .is_some_and(|current| current.connection_id == connection_id)
        {
            *browser = None;
            drop(browser);
            let auto_mode = self.anime_state.read().await.auto_mode;
            let revision = self.anime_revision.fetch_add(1, Ordering::Relaxed) + 1;
            let state = AnimeVostState::unavailable(revision, auto_mode);
            *self.anime_state.write().await = state.clone();
            self.broadcast(&ServerMessage::AnimeState { state }).await;
            tracing::info!(%connection_id, %reason, "local video-site browser bridge disconnected");
        }
    }

    async fn update_anime_state(&self, connection_id: Uuid, mut state: AnimeVostState) {
        let current_browser = self.browser.read().await;
        if !current_browser
            .as_ref()
            .is_some_and(|current| current.connection_id == connection_id)
        {
            return;
        }
        drop(current_browser);
        state = state.sanitize();
        state.revision = self.anime_revision.fetch_add(1, Ordering::Relaxed) + 1;
        *self.anime_state.write().await = state.clone();
        self.broadcast(&ServerMessage::AnimeState { state }).await;
    }

    async fn send_browser_command(&self, command: &BrowserServerMessage) -> Result<()> {
        let text = serde_json::to_string(command).context("failed to serialize browser command")?;
        let browser = self.browser.read().await;
        let connection = browser
            .as_ref()
            .context("video-site browser extension is not connected")?;
        connection
            .outbound
            .send(Message::Text(text.into()))
            .context("video-site browser extension connection is closed")
    }

    fn release_session_buttons(&self, session_id: Uuid) {
        let buttons = self.buttons.lock().release_session(session_id);
        for button in buttons {
            if let Err(error) = self.executor.execute(&Action::MouseButton {
                button,
                state: ButtonState::Up,
            }) {
                tracing::error!(%error, ?button, "failed to release disconnected client's button");
            }
        }
    }

    pub async fn shutdown(&self) {
        if !self.accepting_actions.swap(false, Ordering::SeqCst) {
            return;
        }
        self.shutdown_tx.send_replace(true);
        let clients = self.clients.read().await;
        for client in clients.values() {
            let _ = client.outbound.send(Message::Close(Some(CloseFrame {
                code: close_code::AWAY,
                reason: "server shutdown".into(),
            })));
        }
        drop(clients);
        if let Some(browser) = self.browser.read().await.as_ref() {
            let _ = browser.outbound.send(Message::Close(Some(CloseFrame {
                code: close_code::AWAY,
                reason: "server shutdown".into(),
            })));
        }
        let buttons = self.buttons.lock().release_all();
        for button in buttons {
            let _ = self.executor.execute(&Action::MouseButton {
                button,
                state: ButtonState::Up,
            });
        }
        self.audio.shutdown();
    }

    fn apply_action(&self, session_id: Uuid, action: &Action) -> Result<()> {
        anyhow::ensure!(
            self.accepting_actions.load(Ordering::SeqCst),
            "server is shutting down"
        );
        match action {
            Action::MouseButton {
                button,
                state: ButtonState::Down,
            } => {
                if self.buttons.lock().press(session_id, *button) {
                    self.executor.execute(action)?;
                }
            }
            Action::MouseButton {
                button,
                state: ButtonState::Up,
            } => {
                if self.buttons.lock().release(session_id, *button) {
                    self.executor.execute(action)?;
                }
            }
            Action::SetVolume(value) => self.audio.set_volume(*value)?,
            Action::SetMute(muted) => self.audio.set_mute(*muted)?,
            _ => self.executor.execute(action)?,
        }
        Ok(())
    }

    async fn broadcast(&self, message: &ServerMessage) {
        let Ok(text) = serde_json::to_string(message) else {
            tracing::error!("failed to serialize server message");
            return;
        };
        let clients = self.clients.read().await;
        for client in clients.values() {
            let _ = client.outbound.send(Message::Text(text.clone().into()));
        }
    }
}

pub async fn serve(listener: TcpListener, state: Arc<ServerState>) -> Result<()> {
    let router = Router::new()
        .route("/v1/ws", get(websocket_route))
        .route("/v1/browser", get(browser_websocket_route))
        .with_state(state.clone());
    let shutdown_state = state.clone();
    axum::serve(
        listener,
        router.into_make_service_with_connect_info::<std::net::SocketAddr>(),
    )
    .with_graceful_shutdown(async move {
        shutdown_signal().await;
        shutdown_state.shutdown().await;
    })
    .await
    .context("WebSocket server failed")?;
    Ok(())
}

async fn websocket_route(ws: WebSocketUpgrade, State(state): State<Arc<ServerState>>) -> Response {
    ws.max_message_size(64 * 1024)
        .on_upgrade(move |socket| client_session(socket, state))
}

async fn browser_websocket_route(
    ws: WebSocketUpgrade,
    ConnectInfo(peer): ConnectInfo<std::net::SocketAddr>,
    State(state): State<Arc<ServerState>>,
) -> Response {
    if !peer.ip().is_loopback() {
        tracing::warn!(%peer, "rejected non-loopback browser bridge connection");
        return (StatusCode::FORBIDDEN, "browser bridge is loopback-only").into_response();
    }
    ws.max_message_size(16 * 1024)
        .on_upgrade(move |socket| browser_session(socket, state))
}

async fn client_session(socket: WebSocket, state: Arc<ServerState>) {
    if let Err(error) = run_client_session(socket, state).await {
        tracing::warn!(%error, "WebSocket session failed");
    }
}

async fn run_client_session(socket: WebSocket, state: Arc<ServerState>) -> Result<()> {
    let session_id = Uuid::new_v4();
    let (mut sink, mut stream) = socket.split();
    let (outbound_tx, mut outbound_rx) = mpsc::unbounded_channel::<Message>();
    let writer = tokio::spawn(async move {
        while let Some(message) = outbound_rx.recv().await {
            let is_close = matches!(message, Message::Close(_));
            if sink.send(message).await.is_err() {
                break;
            }
            if is_close {
                break;
            }
        }
        let _ = sink.close().await;
    });

    let first = time::timeout(Duration::from_secs(3), stream.next())
        .await
        .context("client hello timed out")?
        .context("client closed before hello")??;
    let hello = parse_hello(first, &outbound_tx)?;
    if hello.protocol != PROTOCOL_VERSION {
        send_message(
            &outbound_tx,
            &ServerMessage::error(
                "",
                "unsupported_protocol",
                format!(
                    "server protocol is {PROTOCOL_VERSION}, client requested {}",
                    hello.protocol
                ),
            ),
        );
        let _ = outbound_tx.send(Message::Close(Some(CloseFrame {
            code: close_code::POLICY,
            reason: "unsupported protocol".into(),
        })));
        writer.await.ok();
        return Ok(());
    }

    let audio = state.audio.state();
    send_message(
        &outbound_tx,
        &ServerMessage::ServerHello {
            protocol: PROTOCOL_VERSION,
            server_id: state.server_id,
            server_name: state.server_name.clone(),
            capabilities: CAPABILITIES,
            volume: audio.volume,
            muted: audio.muted,
        },
    );
    state
        .register(session_id, &hello, outbound_tx.clone())
        .await;
    let anime_state = state.anime_state.read().await.clone();
    send_message(
        &outbound_tx,
        &ServerMessage::AnimeState { state: anime_state },
    );

    let (pointer_tx, pointer_rx) = watch::channel(None);
    let (scroll_tx, scroll_rx) = watch::channel(None);
    let pointer_worker = spawn_realtime_worker(state.clone(), pointer_rx);
    let scroll_worker = spawn_realtime_worker(state.clone(), scroll_rx);
    let mut last_seen = Instant::now();
    let mut timeout_tick = time::interval(Duration::from_millis(250));
    let mut shutdown_rx = state.shutdown_tx.subscribe();
    let mut overflow_count = 0_u8;
    let reason;

    loop {
        tokio::select! {
            incoming = stream.next() => {
                let Some(incoming) = incoming else {
                    reason = "peer closed";
                    break;
                };
                let message = match incoming {
                    Ok(message) => message,
                    Err(error) => {
                        tracing::debug!(%error, "WebSocket receive error");
                        reason = "receive error";
                        break;
                    }
                };
                last_seen = Instant::now();
                match message {
                    Message::Text(text) => match serde_json::from_str::<ClientMessage>(&text) {
                        Ok(ClientMessage::Control(control)) => {
                            let request_id = control.request_id.clone();
                            match state.control_tx.try_send(ControlJob {
                                session_id,
                                message: control,
                                outbound: outbound_tx.clone(),
                            }) {
                                Ok(()) => overflow_count = 0,
                                Err(_) => {
                                    overflow_count = overflow_count.saturating_add(1);
                                    send_message(&outbound_tx, &ServerMessage::error(
                                        request_id,
                                        "control_queue_full",
                                        "control queue is full",
                                    ));
                                    if overflow_count >= 3 {
                                        let _ = outbound_tx.send(Message::Close(Some(CloseFrame {
                                            code: close_code::POLICY,
                                            reason: "control queue overflow".into(),
                                        })));
                                        reason = "repeated control queue overflow";
                                        break;
                                    }
                                }
                            }
                        }
                        Ok(ClientMessage::Heartbeat { sequence }) => {
                            tracing::trace!(%session_id, sequence, "heartbeat");
                        }
                        Ok(ClientMessage::ClientHello(_)) => {
                            send_message(&outbound_tx, &ServerMessage::error(
                                "",
                                "unexpected_message",
                                "client_hello is allowed only as the first frame",
                            ));
                        }
                        Err(error) => {
                            send_message(&outbound_tx, &ServerMessage::error(
                                "",
                                "invalid_json",
                                error.to_string(),
                            ));
                        }
                    },
                    Message::Binary(bytes) => match RealtimeFrame::decode(&bytes) {
                        Ok(frame) => {
                            let tx = match frame.kind {
                                RealtimeKind::Pointer => &pointer_tx,
                                RealtimeKind::Scroll => &scroll_tx,
                            };
                            tx.send_replace(Some(frame));
                        }
                        Err(error) => {
                            send_message(&outbound_tx, &ServerMessage::error(
                                "",
                                "invalid_realtime_frame",
                                error.to_string(),
                            ));
                        }
                    },
                    Message::Close(frame) => {
                        // Echo the peer close frame so standards-compliant clients can
                        // complete the WebSocket closing handshake before cleanup.
                        let _ = outbound_tx.send(Message::Close(frame));
                        reason = "peer close frame";
                        break;
                    }
                    Message::Ping(payload) => { let _ = outbound_tx.send(Message::Pong(payload)); }
                    Message::Pong(_) => {}
                }
            }
            _ = timeout_tick.tick() => {
                if last_seen.elapsed() >= HEARTBEAT_TIMEOUT {
                    let _ = outbound_tx.send(Message::Close(Some(CloseFrame {
                        code: close_code::AWAY,
                        reason: "heartbeat timeout".into(),
                    })));
                    reason = "heartbeat timeout";
                    break;
                }
            }
            changed = shutdown_rx.changed() => {
                if changed.is_err() || *shutdown_rx.borrow_and_update() {
                    reason = "server shutdown";
                    break;
                }
            }
        }
    }

    pointer_worker.abort();
    scroll_worker.abort();
    state.unregister(session_id, reason).await;
    drop(outbound_tx);
    let _ = writer.await;
    Ok(())
}

async fn browser_session(socket: WebSocket, state: Arc<ServerState>) {
    if let Err(error) = run_browser_session(socket, state).await {
        tracing::warn!(%error, "local video-site browser bridge failed");
    }
}

async fn run_browser_session(socket: WebSocket, state: Arc<ServerState>) -> Result<()> {
    let connection_id = Uuid::new_v4();
    let (mut sink, mut stream) = socket.split();
    let (outbound_tx, mut outbound_rx) = mpsc::unbounded_channel::<Message>();
    let writer = tokio::spawn(async move {
        while let Some(message) = outbound_rx.recv().await {
            let is_close = matches!(message, Message::Close(_));
            if sink.send(message).await.is_err() || is_close {
                break;
            }
        }
        let _ = sink.close().await;
    });

    let first = time::timeout(Duration::from_secs(3), stream.next())
        .await
        .context("browser hello timed out")?
        .context("browser closed before hello")??;
    let Message::Text(text) = first else {
        anyhow::bail!("first browser frame must be browser_hello")
    };
    let BrowserMessage::BrowserHello {
        protocol,
        extension_version,
    } = serde_json::from_str::<BrowserMessage>(&text).context("invalid browser hello JSON")?
    else {
        anyhow::bail!("first browser frame must be browser_hello")
    };
    if protocol != PROTOCOL_VERSION {
        let _ = outbound_tx.send(Message::Close(Some(CloseFrame {
            code: close_code::POLICY,
            reason: "unsupported protocol".into(),
        })));
        writer.await.ok();
        return Ok(());
    }

    send_browser_message(
        &outbound_tx,
        &BrowserServerMessage::BrowserHello {
            protocol: PROTOCOL_VERSION,
        },
    );
    state
        .register_browser(connection_id, outbound_tx.clone(), &extension_version)
        .await;

    let mut last_seen = Instant::now();
    let mut timeout_tick = time::interval(Duration::from_millis(500));
    let mut shutdown_rx = state.shutdown_tx.subscribe();
    let reason;
    loop {
        tokio::select! {
            incoming = stream.next() => {
                let Some(incoming) = incoming else {
                    reason = "peer closed";
                    break;
                };
                let message = match incoming {
                    Ok(message) => message,
                    Err(_) => {
                        reason = "receive error";
                        break;
                    }
                };
                last_seen = Instant::now();
                match message {
                    Message::Text(text) => match serde_json::from_str::<BrowserMessage>(&text) {
                        Ok(BrowserMessage::AnimeState { state: anime_state }) => {
                            state.update_anime_state(connection_id, anime_state).await;
                        }
                        Ok(BrowserMessage::Heartbeat { sequence }) => {
                            tracing::trace!(%connection_id, sequence, "browser bridge heartbeat");
                        }
                        Ok(BrowserMessage::BrowserHello { .. }) => {
                            tracing::debug!(%connection_id, "ignored repeated browser hello");
                        }
                        Err(error) => tracing::debug!(%connection_id, %error, "invalid browser bridge message"),
                    },
                    Message::Close(frame) => {
                        let _ = outbound_tx.send(Message::Close(frame));
                        reason = "peer close frame";
                        break;
                    }
                    Message::Ping(payload) => { let _ = outbound_tx.send(Message::Pong(payload)); }
                    Message::Pong(_) | Message::Binary(_) => {}
                }
            }
            _ = timeout_tick.tick() => {
                if last_seen.elapsed() >= Duration::from_secs(5) {
                    let _ = outbound_tx.send(Message::Close(Some(CloseFrame {
                        code: close_code::AWAY,
                        reason: "heartbeat timeout".into(),
                    })));
                    reason = "heartbeat timeout";
                    break;
                }
            }
            changed = shutdown_rx.changed() => {
                if changed.is_err() || *shutdown_rx.borrow_and_update() {
                    reason = "server shutdown";
                    break;
                }
            }
        }
    }

    state.unregister_browser(connection_id, reason).await;
    drop(outbound_tx);
    let _ = writer.await;
    Ok(())
}

fn parse_hello(message: Message, outbound: &mpsc::UnboundedSender<Message>) -> Result<ClientHello> {
    let Message::Text(text) = message else {
        anyhow::bail!("first frame must be a text client_hello")
    };
    match serde_json::from_str::<ClientMessage>(&text).context("invalid client hello JSON")? {
        ClientMessage::ClientHello(hello) => {
            if hello.device_name.trim().is_empty() || hello.device_name.len() > 128 {
                anyhow::bail!("device_name must contain 1..128 characters")
            }
            Ok(hello)
        }
        _ => {
            send_message(
                outbound,
                &ServerMessage::error("", "expected_hello", "first frame must be client_hello"),
            );
            anyhow::bail!("first frame was not client_hello")
        }
    }
}

fn spawn_realtime_worker(
    state: Arc<ServerState>,
    mut receiver: watch::Receiver<Option<RealtimeFrame>>,
) -> JoinHandle<()> {
    tokio::spawn(async move {
        let mut accumulator = RealtimeAccumulator::default();
        while receiver.changed().await.is_ok() {
            let frame = *receiver.borrow_and_update();
            if let Some(action) = frame.and_then(|frame| accumulator.apply(frame))
                && let Err(error) = state.executor.execute(&action)
            {
                tracing::warn!(%error, ?action, "realtime action failed");
            }
        }
    })
}

async fn control_worker(state: Arc<ServerState>, mut receiver: mpsc::Receiver<ControlJob>) {
    while let Some(job) = receiver.recv().await {
        let request_id = job.message.request_id.clone();
        let reply = match browser_command_from_control(&job.message) {
            Some(Ok(command)) => match state.send_browser_command(&command).await {
                Ok(()) => ServerMessage::ok(request_id),
                Err(error) => ServerMessage::error(
                    request_id,
                    "browser_bridge_unavailable",
                    error.to_string(),
                ),
            },
            Some(Err(error)) => {
                ServerMessage::error(request_id, "invalid_action", error.to_string())
            }
            None => match Action::try_from(job.message) {
                Ok(action) => match state.apply_action(job.session_id, &action) {
                    Ok(()) => ServerMessage::ok(request_id),
                    Err(error) => {
                        ServerMessage::error(request_id, "action_failed", error.to_string())
                    }
                },
                Err(error) => ServerMessage::error(request_id, "invalid_action", error.to_string()),
            },
        };
        send_message(&job.outbound, &reply);
    }
}

async fn audio_broadcaster(state: Arc<ServerState>) {
    let mut audio = state.audio.subscribe();
    while audio.changed().await.is_ok() {
        let AudioState { volume, muted } = *audio.borrow_and_update();
        let revision = state.revision.fetch_add(1, Ordering::Relaxed) + 1;
        state
            .broadcast(&ServerMessage::State {
                revision,
                volume,
                muted,
            })
            .await;
    }
}

fn send_message(outbound: &mpsc::UnboundedSender<Message>, message: &ServerMessage) {
    if let Ok(text) = serde_json::to_string(message) {
        let _ = outbound.send(Message::Text(text.into()));
    }
}

fn send_browser_message(outbound: &mpsc::UnboundedSender<Message>, message: &BrowserServerMessage) {
    if let Ok(text) = serde_json::to_string(message) {
        let _ = outbound.send(Message::Text(text.into()));
    }
}

async fn shutdown_signal() {
    let _ = tokio::signal::ctrl_c().await;
    tracing::info!("shutdown requested");
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{action::MouseButton, executor::RecordingExecutor};

    #[test]
    fn control_queue_is_bounded() {
        assert_eq!(CONTROL_QUEUE_CAPACITY, 128);
    }

    #[test]
    fn release_session_uses_reference_counted_transition() {
        let executor = Arc::new(RecordingExecutor::default());
        let session_a = Uuid::new_v4();
        let session_b = Uuid::new_v4();
        let mut ownership = ButtonOwnership::default();
        assert!(ownership.press(session_a, MouseButton::Left));
        assert!(!ownership.press(session_b, MouseButton::Left));
        assert!(ownership.release_session(session_a).is_empty());
        assert_eq!(
            ownership.release_session(session_b),
            vec![MouseButton::Left]
        );
        assert!(executor.actions.lock().is_empty());
    }
}
