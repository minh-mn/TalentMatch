// Trang tìm dự án: bộ lọc GET từ dữ liệu MySQL, giữ trạng thái khi phân trang.
(() => {
  'use strict';
  const form = document.getElementById('tm28Filters');
  if (!form) return;
  const search = document.getElementById('tm28Search');
  const query = document.getElementById('tm28Query');
  const sort = document.getElementById('tm28Sort');
  const min = form.querySelector('[name="minBudget"]');
  const max = form.querySelector('[name="maxBudget"]');
  const budgetRanges = [...form.querySelectorAll('[name="budgetRange"]')];
  const hiddenQ = form.querySelector('input[name="q"]');
  const hiddenCategory = form.querySelector('input[name="category"]');
  const hiddenSort = form.querySelector('input[name="sort"]');
  const sync = () => {hiddenQ.value = query.value.trim(); hiddenSort.value = sort.value;};
  const apply = () => {sync(); form.requestSubmit();};
  budgetRanges.forEach(r => r.addEventListener('change', () => {
    const values = {all:['',''],under5:['','4999999'], '5to15':['5000000','15000000'],over15:['15000001','']};
    [min.value,max.value] = values[r.value];
    apply();
  }));
  [min,max].forEach(el => el.addEventListener('input', () => { min.setCustomValidity(''); budgetRanges.forEach(r => r.checked = false); }));
  form.addEventListener('submit', e => {
    sync();
    if (min.value && max.value && Number(min.value) > Number(max.value)) {
      e.preventDefault(); min.setCustomValidity('Ngân sách tối thiểu phải nhỏ hơn hoặc bằng ngân sách tối đa.'); min.reportValidity();
    } else min.setCustomValidity('');
  });
  search.addEventListener('submit', e => {e.preventDefault(); apply();});
  sort.addEventListener('change', apply);
  form.querySelectorAll('input[type="checkbox"]').forEach(el => el.addEventListener('change', apply));
  document.querySelectorAll('.jb-categories [data-category]').forEach(a => a.addEventListener('click', e => {
    if (e.ctrlKey || e.metaKey || e.shiftKey || e.altKey) return;
    e.preventDefault(); hiddenCategory.value = a.dataset.category || ''; apply();
  }));
  document.querySelectorAll('.jb-pagination a:not(.disabled)').forEach(a => a.addEventListener('click', e => {
    if (e.ctrlKey || e.metaKey || e.shiftKey || e.altKey) return;
    e.preventDefault(); sync();
    const params = new URLSearchParams(new FormData(form));
    params.delete('budgetRange');
    params.set('page', new URL(a.href).searchParams.get('page') || '1');
    window.location.assign(form.action + '?' + params.toString());
  }));
})();
