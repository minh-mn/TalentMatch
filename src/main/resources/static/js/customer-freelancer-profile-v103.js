/* TalentMatch V103: Client-only profile interactions. */
(function () {
  'use strict';
  const modal = document.getElementById('tmfp-invite-modal');
  let lastFocus = null;

  function openInvite() {
    if (!modal) return;
    lastFocus = document.activeElement;
    modal.hidden = false;
    document.body.style.overflow = 'hidden';
    const jobSelect = modal.querySelector('select[name="jobId"]');
    if (jobSelect) jobSelect.focus();
  }
  function closeInvite() {
    if (!modal) return;
    modal.hidden = true;
    document.body.style.overflow = '';
    if (lastFocus && lastFocus.focus) lastFocus.focus();
  }
  document.querySelectorAll('[data-invite-open]').forEach(function (button) {
    button.addEventListener('click', openInvite);
  });
  document.querySelectorAll('[data-invite-close]').forEach(function (button) {
    button.addEventListener('click', closeInvite);
  });
  document.addEventListener('keydown', function (event) {
    if (event.key === 'Escape' && modal && !modal.hidden) closeInvite();
  });

  const share = document.getElementById('tmfp-share');
  if (share) {
    share.addEventListener('click', async function () {
      const link = window.location.href;
      try {
        await navigator.clipboard.writeText(link);
        const label = share.innerHTML;
        share.textContent = 'Đã sao chép liên kết';
        setTimeout(function () { share.innerHTML = label; }, 1900);
      } catch (err) {
        window.prompt('Sao chép đường dẫn hồ sơ:', link);
      }
    });
  }

  const tabs = Array.from(document.querySelectorAll('.tmfp-tabs a[href^="#"]'));
  tabs.forEach(function (tab) {
    tab.addEventListener('click', function () {
      tabs.forEach(function (t) { t.classList.remove('active'); });
      tab.classList.add('active');
    });
  });
})();
