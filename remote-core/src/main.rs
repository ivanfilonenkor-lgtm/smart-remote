use std::{net::SocketAddr, str::FromStr};

use anyhow::{Context, Result};
use clap::Parser;
use remote_core::{
    DEFAULT_PORT, PROTOCOL_VERSION,
    audio::AudioHandle,
    discovery::DiscoveryAdvertiser,
    executor::platform_executor,
    network::private_ipv4_addresses,
    server::{ServerState, serve},
};
use tokio::net::TcpListener;
use tracing_subscriber::EnvFilter;

#[derive(Debug, Parser)]
#[command(version, about)]
struct Args {
    /// Address on which the trusted-LAN server listens.
    #[arg(long, default_value = "0.0.0.0")]
    bind: String,
    /// TCP port for ws://.../v1/ws.
    #[arg(long, default_value_t = DEFAULT_PORT)]
    port: u16,
    /// Tracing filter, for example "info" or "remote_core=debug".
    #[arg(long, default_value = "info")]
    log_level: String,
}

#[tokio::main]
async fn main() -> Result<()> {
    let args = Args::parse();
    tracing_subscriber::fmt()
        .with_env_filter(EnvFilter::try_new(&args.log_level).context("invalid --log-level")?)
        .with_target(false)
        .init();

    let address = SocketAddr::from_str(&format!("{}:{}", args.bind, args.port))
        .context("invalid --bind or --port")?;
    let listener = TcpListener::bind(address)
        .await
        .with_context(|| format!("failed to bind {address}"))?;

    println!("Smart Remote RemoteCore v{}", env!("CARGO_PKG_VERSION"));
    println!("Protocol: {PROTOCOL_VERSION}");
    println!("WebSocket endpoint: ws://<address>:{}/v1/ws", args.port);
    let addresses = private_ipv4_addresses();
    if addresses.is_empty() {
        println!("Private LAN address: none detected (check network configuration)");
    } else {
        for ip in &addresses {
            println!("Private LAN endpoint: ws://{ip}:{}/v1/ws", args.port);
        }
    }
    println!();
    println!("WARNING: protocol v1 has NO authentication or encryption.");
    println!("Use only on a trusted Private LAN with the LocalSubnet firewall rule.");
    println!("Press Ctrl+C to stop and release all held buttons.");

    let audio = AudioHandle::spawn().context("failed to start Core Audio worker")?;
    let server_name = std::env::var("COMPUTERNAME").unwrap_or_else(|_| "Windows PC".into());
    let state = ServerState::start(server_name.clone(), platform_executor(), audio);
    let discovery = match DiscoveryAdvertiser::start(&server_name, state.server_id(), args.port) {
        Ok(discovery) => {
            println!("Network discovery: available as {server_name}");
            Some(discovery)
        }
        Err(error) => {
            tracing::warn!(%error, "automatic LAN discovery is unavailable; manual IP still works");
            None
        }
    };

    let result = serve(listener, state).await;
    if let Some(discovery) = discovery {
        discovery.shutdown();
    }
    result
}
