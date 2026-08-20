package com.example.nfcreader;

/** 设备 NFC 能力的当前状态。 */
public enum NfcAvailability {
    /** 设备支持 NFC 且系统开关已开启。 */
    AVAILABLE,
    /** 设备支持 NFC，但系统开关未开启。 */
    DISABLED,
    /** 设备没有可用的 NFC Adapter。 */
    UNSUPPORTED
}
