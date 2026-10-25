type IOSNavigator = Pick<Navigator, 'userAgent' | 'platform' | 'maxTouchPoints'>;

export function isIOSDevice(device: IOSNavigator = navigator) {
  return /iPhone|iPad|iPod/i.test(device.userAgent)
    || (device.platform === 'MacIntel' && device.maxTouchPoints > 1);
}

export function isIOSSafari(device: IOSNavigator = navigator) {
  return isIOSDevice(device)
    && /Version\/[\d.]+.*Safari\//i.test(device.userAgent)
    && !/CriOS|FxiOS|EdgiOS|OPiOS|GSA|MicroMessenger|QQ\//i.test(device.userAgent);
}
