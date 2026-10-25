import { useCallback, useEffect, useState } from 'react';
import { Share } from 'lucide-react';
import { isIOSDevice, isIOSSafari } from './ios-install';
import { localRead, localWrite } from './storage';
import './ios-install-guide.css';

export function useIOSInstallGuide(installed: boolean) {
  const ios = isIOSDevice();
  const [open, setOpen] = useState(() => ios && !installed && !localRead('ios-install-guide-seen', false));
  useEffect(() => { if (installed) setOpen(false); }, [installed]);
  const dismiss = useCallback(() => {
    if (ios) localWrite('ios-install-guide-seen', true);
    setOpen(false);
  }, [ios]);
  return { open: ios && open && !installed, dismiss };
}

export function IOSInstallSteps() {
  return <div className="ios-install-guide">
    {isIOSDevice() && !isIOSSafari() && <p className="ios-install-guide-note">请先在 Safari 中打开此页面。</p>}
    <ol className="ios-install-guide-steps">
      <li><span>1</span><div><strong>点击 Safari 分享 <Share size={18} aria-label="Safari 分享图标" /></strong></div></li>
      <li><span>2</span><div><strong>选择“添加到主屏幕”</strong></div></li>
      <li><span>3</span><div><strong>点击“添加”</strong><p>如有“作为网页 App 打开”，请保持开启。</p></div></li>
    </ol>
    <p className="ios-install-guide-note">添加后，从主屏幕打开 PSQ。</p>
  </div>;
}
