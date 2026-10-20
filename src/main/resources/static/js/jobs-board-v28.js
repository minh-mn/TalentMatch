/* TalentMatch Job Board — bộ lọc GET đồng bộ với backend. */
(() => {
  'use strict';

  const form = document.getElementById('tm28Filters');
  if (!form) return;

  const query = document.getElementById('tm28Query');
  const search = document.getElementById('tm28Search');
  const sort = document.getElementById('tm28Sort');
  const category = document.getElementById('tm28CategorySelect');
  const duration = document.getElementById('tm28DurationSelect');
  const budgetMin = form.elements.namedItem('minBudget');
  const budgetMax = form.elements.namedItem('maxBudget');
  const workAll = document.getElementById('tm28AllWork');
  const workItems = [...form.querySelectorAll('[name="workType"]')];

  const hiddenQ = form.elements.namedItem('q');
  const hiddenSort = form.elements.namedItem('sort');
  const hiddenCategory = form.elements.namedItem('category');

  const syncSharedState = () => {
    if (hiddenQ && query) hiddenQ.value = query.value.trim();
    if (hiddenSort && sort) hiddenSort.value = sort.value;
    if (hiddenCategory && category) hiddenCategory.value = category.value;
    if (workAll && workAll.checked) workItems.forEach(item => item.checked = false);
  };

  const submitFilters = () => {
    syncSharedState();
    form.requestSubmit();
  };

  const validateBudget = () => {
    if (!budgetMin || !budgetMax) return true;
    budgetMin.setCustomValidity('');
    const min = budgetMin.value === '' ? null : Number(budgetMin.value);
    const max = budgetMax.value === '' ? null : Number(budgetMax.value);
    if ((min !== null && min < 0) || (max !== null && max < 0)) {
      budgetMin.setCustomValidity('Ngân sách không được là số âm.');
      budgetMin.reportValidity();
      return false;
    }
    if (min !== null && max !== null && min > max) {
      budgetMin.setCustomValidity('Ngân sách tối thiểu không được lớn hơn ngân sách tối đa.');
      budgetMin.reportValidity();
      return false;
    }
    return true;
  };

  form.addEventListener('submit', event => {
    syncSharedState();
    if (!validateBudget()) event.preventDefault();
  });

  if (search) {
    search.addEventListener('submit', event => {
      event.preventDefault();
      submitFilters();
    });
  }

  if (sort) sort.addEventListener('change', submitFilters);
  if (category) category.addEventListener('change', submitFilters);
  if (duration) duration.addEventListener('change', submitFilters);

  form.querySelectorAll('.tm28-filter-option input').forEach(input => {
    input.addEventListener('change', () => {
      if (input === workAll && workAll.checked) {
        workItems.forEach(item => item.checked = false);
      } else if (workItems.includes(input) && workAll) {
        workAll.checked = !workItems.some(item => item.checked);
      }
      submitFilters();
    });
  });

  form.querySelectorAll('select').forEach(select => {
    if (select === category || select === duration) return;
    select.addEventListener('change', submitFilters);
  });

  const setBudgetActive = () => {
    form.querySelectorAll('[data-budget-min]').forEach(button => {
      const sameMin = button.dataset.budgetMin === (budgetMin?.value || '');
      const sameMax = button.dataset.budgetMax === (budgetMax?.value || '');
      button.classList.toggle('active', sameMin && sameMax);
    });
  };

  setBudgetActive();
  [budgetMin, budgetMax].filter(Boolean).forEach(input => {
    input.addEventListener('input', () => {
      if (budgetMin) budgetMin.setCustomValidity('');
      setBudgetActive();
    });
  });

  form.querySelectorAll('[data-budget-min]').forEach(button => {
    button.addEventListener('click', () => {
      const active = button.classList.contains('active');
      if (budgetMin) budgetMin.value = active ? '' : button.dataset.budgetMin;
      if (budgetMax) budgetMax.value = active ? '' : button.dataset.budgetMax;
      setBudgetActive();
      submitFilters();
    });
  });

  document.querySelectorAll('.tm28-categories [data-category]').forEach(link => {
    link.addEventListener('click', event => {
      if (event.ctrlKey || event.metaKey || event.shiftKey || event.altKey || event.button !== 0) return;
      event.preventDefault();
      if (category) category.value = link.dataset.category || '';
      submitFilters();
    });
  });

  /* Phân trang luôn lấy toàn bộ trạng thái hiện tại từ form để không mất checkbox/filter. */
  document.querySelectorAll('.tm28-pagination a:not(.disabled)').forEach(link => {
    link.addEventListener('click', event => {
      if (event.ctrlKey || event.metaKey || event.shiftKey || event.altKey || event.button !== 0) return;
      event.preventDefault();
      syncSharedState();
      const targetPage = new URL(link.href, window.location.href).searchParams.get('page') || '1';
      const params = new URLSearchParams(new FormData(form));
      params.set('page', targetPage);
      window.location.assign(form.action + '?' + params.toString());
    });
  });
})();
