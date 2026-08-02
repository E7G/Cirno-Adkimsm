//! Small, allocation-free policy core shared by the Android UI and system hook.
//! Keep Android reflection and Binder calls in Java; keep hot-path policy here.

use std::ffi::c_void;

const FLAG_SYSTEM: i32 = 1 << 0;
const FLAG_VISIBLE: i32 = 1 << 1;
const FLAG_LOCATION: i32 = 1 << 2;
const FLAG_AUDIO: i32 = 1 << 3;
const FLAG_RECORDING: i32 = 1 << 4;
const FLAG_VPN: i32 = 1 << 5;
const FLAG_WHITELISTED: i32 = 1 << 6;
const FLAG_CLOVER: i32 = 1 << 7;
const FLAG_LOW_MEMORY: i32 = 1 << 8;
const FLAG_NETWORK_ACTIVE: i32 = 1 << 9;

fn should_freeze(flags: i32, process_count: i32) -> bool {
    if process_count <= 0 {
        return false;
    }
    let exemptions = FLAG_SYSTEM
        | FLAG_VISIBLE
        | FLAG_LOCATION
        | FLAG_AUDIO
        | FLAG_RECORDING
        | FLAG_VPN
        | FLAG_WHITELISTED
        | FLAG_NETWORK_ACTIVE;
    flags & exemptions == 0
}

fn delay_ms(configured_seconds: i32) -> i64 {
    i64::from(configured_seconds.clamp(1, 60)) * 1_000
}

#[no_mangle]
pub extern "system" fn Java_nep_timeline_cirno_nativecore_NativePolicy_nativeShouldFreeze(
    _env: *mut c_void,
    _class: *mut c_void,
    flags: i32,
    process_count: i32,
) -> u8 {
    should_freeze(flags, process_count) as u8
}

#[no_mangle]
pub extern "system" fn Java_nep_timeline_cirno_nativecore_NativePolicy_nativeDelayMs(
    _env: *mut c_void,
    _class: *mut c_void,
    configured_seconds: i32,
    flags: i32,
) -> i64 {
    // A bounded delay prevents malformed JSON from creating a busy loop or a
    // very long stale queue. Clover keeps the configured value; the low-memory
    // flag only avoids reducing it, because frequent thaw/freeze cycles cost
    // more battery than leaving a cached process alone briefly.
    let _clover = flags & FLAG_CLOVER != 0;
    let _low_memory = flags & FLAG_LOW_MEMORY != 0;
    delay_ms(configured_seconds)
}

#[no_mangle]
pub extern "system" fn Java_nep_timeline_cirno_nativecore_NativePolicy_nativeCoreVersion(
    _env: *mut c_void,
    _class: *mut c_void,
) -> i32 {
    1
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn active_process_is_freezable() {
        assert!(should_freeze(0, 1));
        assert!(!should_freeze(0, 0));
    }

    #[test]
    fn protected_process_is_not_freezable() {
        assert!(!should_freeze(FLAG_VISIBLE, 1));
        assert!(!should_freeze(FLAG_NETWORK_ACTIVE, 2));
    }

    #[test]
    fn delay_is_bounded() {
        assert_eq!(delay_ms(-10), 1_000);
        assert_eq!(delay_ms(5), 5_000);
        assert_eq!(delay_ms(999), 60_000);
    }
}
