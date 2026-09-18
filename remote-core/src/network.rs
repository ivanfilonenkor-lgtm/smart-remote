use std::net::{IpAddr, Ipv4Addr};

pub fn private_ipv4_addresses() -> Vec<Ipv4Addr> {
    let mut addresses = if_addrs::get_if_addrs()
        .unwrap_or_default()
        .into_iter()
        .filter_map(|interface| match interface.ip() {
            IpAddr::V4(ip) if is_private_or_link_local(ip) && !ip.is_loopback() => Some(ip),
            _ => None,
        })
        .collect::<Vec<_>>();
    addresses.sort_unstable();
    addresses.dedup();
    addresses
}

fn is_private_or_link_local(ip: Ipv4Addr) -> bool {
    ip.is_private() || ip.is_link_local()
}
