use std::time::Duration;

use anyhow::{Context, Result};
use mdns_sd::{DaemonEvent, ServiceDaemon, ServiceInfo};
use uuid::Uuid;

use crate::PROTOCOL_VERSION;

pub const SERVICE_TYPE: &str = "_smartremote._tcp.local.";

/// Keeps the DNS-SD announcement alive for the lifetime of RemoteCore.
pub struct DiscoveryAdvertiser {
    daemon: ServiceDaemon,
    fullname: String,
    stopped: bool,
}

impl DiscoveryAdvertiser {
    pub fn start(server_name: &str, server_id: Uuid, port: u16) -> Result<Self> {
        let daemon = ServiceDaemon::new().context("failed to create mDNS daemon")?;
        let monitor = daemon.monitor().context("failed to monitor mDNS daemon")?;
        std::thread::Builder::new()
            .name("remote-core-mdns-monitor".into())
            .spawn(move || {
                while let Ok(event) = monitor.recv() {
                    match event {
                        DaemonEvent::Error(error) => {
                            tracing::warn!(%error, "mDNS discovery error");
                        }
                        DaemonEvent::NameChange(change) => {
                            tracing::info!(
                                original = %change.original,
                                new_name = %change.new_name,
                                "mDNS resolved a name conflict"
                            );
                        }
                        _ => {}
                    }
                }
            })
            .context("failed to start mDNS monitor")?;

        let info = service_info(server_name, server_id, port)?;
        let fullname = info.get_fullname().to_owned();
        daemon
            .register(info)
            .context("failed to register mDNS service")?;

        Ok(Self {
            daemon,
            fullname,
            stopped: false,
        })
    }

    pub fn shutdown(mut self) {
        self.stop();
    }

    fn stop(&mut self) {
        if self.stopped {
            return;
        }
        self.stopped = true;

        match self.daemon.unregister(&self.fullname) {
            Ok(receiver) => {
                let _ = receiver.recv_timeout(Duration::from_millis(750));
            }
            Err(error) => tracing::debug!(%error, "mDNS service was already unavailable"),
        }
        match self.daemon.shutdown() {
            Ok(receiver) => {
                let _ = receiver.recv_timeout(Duration::from_millis(750));
            }
            Err(error) => tracing::debug!(%error, "mDNS daemon was already stopped"),
        }
    }
}

impl Drop for DiscoveryAdvertiser {
    fn drop(&mut self) {
        self.stop();
    }
}

fn service_info(server_name: &str, server_id: Uuid, port: u16) -> Result<ServiceInfo> {
    let instance_name = dns_sd_instance_name(server_name);
    let hostname = format!(
        "smartremote-{}.local.",
        &server_id.simple().to_string()[..8]
    );
    let server_id = server_id.to_string();
    let protocol = PROTOCOL_VERSION.to_string();
    let properties = [
        ("protocol", protocol.as_str()),
        ("server_id", server_id.as_str()),
        ("server_name", server_name),
        ("path", "/v1/ws"),
    ];

    ServiceInfo::new(
        SERVICE_TYPE,
        &instance_name,
        &hostname,
        "",
        port,
        &properties[..],
    )
    .context("failed to build mDNS service record")
    .map(ServiceInfo::enable_addr_auto)
}

fn dns_sd_instance_name(server_name: &str) -> String {
    let trimmed = server_name.trim();
    let source = if trimmed.is_empty() {
        "Windows PC"
    } else {
        trimmed
    };

    let mut end = source.len().min(60);
    while !source.is_char_boundary(end) {
        end -= 1;
    }
    source[..end].to_owned()
}

#[cfg(test)]
mod tests {
    use super::*;
    use mdns_sd::ServiceEvent;
    use std::time::Instant;

    #[test]
    fn instance_name_is_non_empty_and_utf8_safe() {
        assert_eq!(dns_sd_instance_name("   "), "Windows PC");
        let long = "Компьютер-компьютер-компьютер-компьютер";
        let result = dns_sd_instance_name(long);
        assert!(result.len() <= 60);
        assert!(result.is_char_boundary(result.len()));
    }

    #[test]
    fn service_record_contains_protocol_and_endpoint() {
        let id = Uuid::parse_str("93e17ecb-4075-4388-9465-d20b6ea4a7fd").unwrap();
        let info = service_info("Living room PC", id, 8765).unwrap();

        assert_eq!(info.get_type(), SERVICE_TYPE);
        assert_eq!(info.get_port(), 8765);
        assert!(info.get_fullname().starts_with("Living room PC."));
        assert_eq!(
            info.get_properties().get_property_val_str("protocol"),
            Some("1")
        );
        assert_eq!(
            info.get_properties().get_property_val_str("path"),
            Some("/v1/ws")
        );
    }

    #[test]
    #[ignore = "requires multicast networking on the host"]
    fn advertised_service_is_discoverable() {
        let id = Uuid::new_v4();
        let advertiser = DiscoveryAdvertiser::start("Discovery test PC", id, 38765).unwrap();
        let browser = ServiceDaemon::new().unwrap();
        let events = browser.browse(SERVICE_TYPE).unwrap();
        let deadline = Instant::now() + Duration::from_secs(5);
        let mut found = false;

        while Instant::now() < deadline {
            let remaining = deadline.saturating_duration_since(Instant::now());
            let Ok(event) = events.recv_timeout(remaining) else {
                break;
            };
            if let ServiceEvent::ServiceResolved(info) = event
                && info.get_properties().get_property_val_str("server_id")
                    == Some(id.to_string().as_str())
            {
                found = true;
                break;
            }
        }

        let _ = browser.stop_browse(SERVICE_TYPE);
        if let Ok(receiver) = browser.shutdown() {
            let _ = receiver.recv_timeout(Duration::from_secs(1));
        }
        advertiser.shutdown();
        assert!(found, "mDNS service was not resolved within five seconds");
    }
}
