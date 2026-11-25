/* Đồng bộ số thông báo chưa đọc khi trang đang mở; endpoint chỉ trả dữ liệu của người đăng nhập. */
(function () {
 'use strict';
 const MATCH='a[href="/notifications"], a[href^="/notifications?"]';
 function badgeFor(a,count) {
  if (!a || a.closest('.market-sidebar') && !a.querySelector('.notification-count-badge, .tm184-badge, .market-notify-count')) return;
  let badge=a.querySelector('.notification-count-badge, .tm184-badge, .market-notify-count');
  if(count <= 0){if(badge)badge.remove();a.setAttribute('aria-label','Thông báo, không có thông báo chưa đọc');return;}
  a.setAttribute('aria-label',`${count} thông báo chưa đọc`);
  if (!badge){badge=document.createElement('span');badge.className='notification-count-badge tm184-badge';a.appendChild(badge);}
  badge.textContent=count>99?'99+':String(count);
 }
 async function update() {
  if(document.hidden)return;
  try {
   const response=await fetch('/notifications/unread-count',{credentials:'same-origin',headers:{'Accept':'application/json'},cache:'no-store'});
   if(!response.ok)return;
   const obj=await response.json();const count=Number(obj.count);
   if(!Number.isFinite(count)||count<0)return;
   document.querySelectorAll('.market-sidebar .tm155-count').forEach(el => {el.textContent=count>99?'99+':String(count);el.style.display=count>0?'':'none';});
   document.querySelectorAll(MATCH).forEach(a=>{
    // Chỉ gắn huy hiệu trên chuông, không gắn vào menu và footer.
    if(a.classList.contains('market-notify') || a.classList.contains('admin147-bell') || a.querySelector('.fa-bell, .notification-count-badge, .tm184-badge, .market-notify-count'))badgeFor(a,count);
   });
  } catch(e) { /* Giữ số đã render nếu mạng không sẵn sàng. */ }
 }
 document.addEventListener('DOMContentLoaded',()=>{update();setInterval(update,20000);});
 document.addEventListener('visibilitychange',()=>{if(!document.hidden)update();});
})();
