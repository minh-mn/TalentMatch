(() => {
  const sidebar = document.querySelector('.app-sidebar');
  document.querySelectorAll('[data-sidebar-open]').forEach(b => b.addEventListener('click', () => sidebar?.classList.add('open')));
  document.querySelectorAll('[data-sidebar-close]').forEach(b => b.addEventListener('click', () => sidebar?.classList.remove('open')));
  document.querySelectorAll('[data-nav-toggle]').forEach(b => b.addEventListener('click', () => document.querySelector('[data-main-nav]')?.classList.toggle('open')));

  document.querySelectorAll('[data-confirm]').forEach(form => form.addEventListener('submit', e => {
    const text = form.getAttribute('data-confirm') || 'Bạn chắc chắn muốn thực hiện thao tác này?';
    if (!window.confirm(text)) e.preventDefault();
  }));

  document.querySelectorAll('.flash').forEach(el => setTimeout(() => el.classList.add('fade'), 6500));

  // Profile dropdown like the reference screenshots.
  document.querySelectorAll('[data-user-menu]').forEach(menu => {
    const trigger = menu.querySelector('[data-user-trigger]');
    const dropdown = menu.querySelector('[data-user-dropdown]');
    trigger?.addEventListener('click', e => {
      e.stopPropagation();
      dropdown?.classList.toggle('open');
    });
    dropdown?.addEventListener('click', e => e.stopPropagation());
  });
  document.addEventListener('click', () => {
    document.querySelectorAll('[data-user-dropdown].open').forEach(el => el.classList.remove('open'));
  });

  // Ctrl/Cmd + K focuses marketplace search.
  document.addEventListener('keydown', e => {
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
      const input = document.querySelector('.market-global-search input');
      if (input) {
        e.preventDefault();
        input.focus();
        input.select();
      }
    }
  });

  // Job marketplace filter UI: đồng bộ radio/checkbox với URL và hidden min/max.
  const jobFilterForm = document.getElementById('jobFilterForm');
  if (jobFilterForm) {
    const minBudgetField = document.getElementById('minBudgetField');
    const maxBudgetField = document.getElementById('maxBudgetField');

    const refreshFilterLabel = input => {
      const label = input.closest('label');
      if (label) label.classList.toggle('is-selected', input.checked);
    };

    jobFilterForm.querySelectorAll('input[type="radio"], input[type="checkbox"]').forEach(input => {
      refreshFilterLabel(input);
      input.addEventListener('change', () => {
        if (input.name === 'budgetPreset' && input.checked) {
          if (minBudgetField) minBudgetField.value = input.dataset.budgetMin || '';
          if (maxBudgetField) maxBudgetField.value = input.dataset.budgetMax || '';
          jobFilterForm.querySelectorAll('input[name="budgetPreset"]').forEach(refreshFilterLabel);
        } else {
          refreshFilterLabel(input);
        }
      });
    });

    jobFilterForm.addEventListener('submit', () => {
      const budget = jobFilterForm.querySelector('input[name="budgetPreset"]:checked');
      if (budget) {
        if (minBudgetField) minBudgetField.value = budget.dataset.budgetMin || '';
        if (maxBudgetField) maxBudgetField.value = budget.dataset.budgetMax || '';
      }
    });
  }

  // Toggle password visibility for auth screens.
  document.querySelectorAll('[data-password-toggle]').forEach(btn => {
    btn.addEventListener('click', () => {
      const target = document.getElementById(btn.getAttribute('data-password-toggle'));
      if (!target) return;
      target.type = target.type === 'password' ? 'text' : 'password';
      const icon = btn.querySelector('i');
      if (icon) {
        icon.className = target.type === 'password' ? 'fa-regular fa-eye' : 'fa-regular fa-eye-slash';
      } else {
        btn.textContent = target.type === 'password' ? 'Hiện' : 'Ẩn';
      }
    });
  });


  // V19 profile: mở/đóng form chỉnh sửa mà không chuyển sang workspace cũ.
  const profileEditPanel = document.getElementById('profile-edit');
  const openProfileEdit = () => {
    if (!profileEditPanel) return;
    profileEditPanel.hidden = false;
    requestAnimationFrame(() => profileEditPanel.scrollIntoView({ behavior: 'smooth', block: 'start' }));
  };
  const closeProfileEdit = () => {
    if (!profileEditPanel) return;
    profileEditPanel.hidden = true;
    if (window.location.hash === '#profile-edit') history.replaceState(null, '', window.location.pathname + window.location.search);
  };
  document.querySelectorAll('[data-profile-edit-toggle]').forEach(btn => btn.addEventListener('click', openProfileEdit));
  document.querySelectorAll('[data-profile-edit-close]').forEach(btn => btn.addEventListener('click', closeProfileEdit));
  if (profileEditPanel && window.location.hash === '#profile-edit') openProfileEdit();

  // CV button: giao diện chọn PDF hoạt động ngay, hiển thị tên file đã chọn.
  const cvInput = document.querySelector('[data-cv-input]');
  const cvName = document.querySelector('[data-cv-name]');
  cvInput?.addEventListener('change', () => {
    const file = cvInput.files?.[0];
    if (!file) {
      if (cvName) cvName.textContent = '';
      return;
    }
    if (file.type !== 'application/pdf' && !file.name.toLowerCase().endsWith('.pdf')) {
      cvInput.value = '';
      if (cvName) cvName.textContent = 'Vui lòng chọn đúng file PDF.';
      return;
    }
    if (cvName) cvName.textContent = `Đã chọn: ${file.name}`;
  });


  // V22 profile statistics: lọc bằng AJAX, không reload toàn trang.
  const statsSection = document.getElementById('profile-statistics');
  const statsForm = document.getElementById('profileStatisticsForm');
  if (statsSection && statsForm) {
    const endpoint = statsSection.getAttribute('data-stats-endpoint') || '/freelancer/profile/statistics';
    const submitBtn = statsForm.querySelector('[data-stats-submit]');
    const resetBtn = statsForm.querySelector('[data-stats-reset]');
    const message = statsSection.querySelector('[data-stats-message]');
    const rangeSummary = statsSection.querySelector('[data-stats-range-summary]');
    const barTitle = statsSection.querySelector('[data-bar-title]');
    const barDescription = statsSection.querySelector('[data-bar-description]');
    const barTotalLabel = statsSection.querySelector('[data-bar-total-label]');
    const barTotal = statsSection.querySelector('[data-bar-total]');
    const barScroll = statsSection.querySelector('[data-bar-scroll]');
    const barChart = statsSection.querySelector('[data-bar-chart]');
    const barEmpty = statsSection.querySelector('[data-bar-empty]');
    const pieDescription = statsSection.querySelector('[data-pie-description]');
    const pieTotal = statsSection.querySelector('[data-pie-total]');
    const pieLayout = statsSection.querySelector('[data-pie-layout]');
    const pieChart = statsSection.querySelector('[data-pie-chart]');
    const pieCenter = statsSection.querySelector('[data-pie-center]');
    const pieLegend = statsSection.querySelector('[data-pie-legend]');
    const pieEmpty = statsSection.querySelector('[data-pie-empty]');
    const colors = ['#6c3bff', '#2f6fed', '#13bfa6', '#ffb020', '#f0447d', '#7b8aa4'];
    let requestController = null;

    const numberVN = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 });
    const formatDateVN = iso => {
      if (!iso) return '';
      const parts = iso.split('-');
      return parts.length === 3 ? `${parts[2]}/${parts[1]}/${parts[0]}` : iso;
    };
    const localISO = date => {
      const y = date.getFullYear();
      const m = String(date.getMonth() + 1).padStart(2, '0');
      const d = String(date.getDate()).padStart(2, '0');
      return `${y}-${m}-${d}`;
    };
    const escapeHtml = value => String(value ?? '')
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#039;');

    const showMessage = (textValue, type = 'error') => {
      if (!message) return;
      if (!textValue) {
        message.hidden = true;
        message.textContent = '';
        message.className = 'profile-chart-message';
        return;
      }
      message.textContent = textValue;
      message.className = `profile-chart-message ${type}`;
      message.hidden = false;
    };

    const setLoading = loading => {
      statsSection.classList.toggle('is-loading', loading);
      if (submitBtn) {
        submitBtn.disabled = loading;
        submitBtn.innerHTML = loading
          ? '<i class="fa-solid fa-spinner fa-spin"></i> Đang cập nhật'
          : '<i class="fa-solid fa-chart-simple"></i> Áp dụng';
      }
    };

    const buildPieGradient = slices => {
      if (!Array.isArray(slices) || !slices.length) return 'conic-gradient(#eef0f7 0 100%)';
      let start = 0;
      const parts = slices.map((slice, index) => {
        const percent = Number(slice.percent || 0);
        const end = start + percent;
        const part = `${colors[index % colors.length]} ${start.toFixed(2)}% ${end.toFixed(2)}%`;
        start = end;
        return part;
      });
      return `conic-gradient(${parts.join(',')})`;
    };

    const renderBars = data => {
      const rows = Array.isArray(data.barData) ? data.barData : [];
      const max = Number(data.barMax || 1);
      const total = Number(data.barTotal || 0);
      const revenue = data.metric === 'revenue';

      if (barTitle) barTitle.textContent = revenue ? 'Doanh thu theo thời gian' : 'Số dự án theo thời gian';
      if (barDescription) barDescription.textContent = revenue
        ? 'Doanh thu thực nhận (đã giải ngân) theo từng ngày trong khoảng đã chọn.'
        : 'Số dự án được tạo theo từng ngày trong khoảng đã chọn.';
      if (barTotalLabel) barTotalLabel.textContent = revenue ? 'Tổng doanh thu' : 'Tổng dự án';
      if (barTotal) barTotal.textContent = revenue ? `${numberVN.format(total)}đ` : numberVN.format(total);

      const hasData = total > 0;
      if (barEmpty) barEmpty.hidden = hasData;
      if (barScroll) barScroll.hidden = !hasData;
      if (!barChart) return;

      barChart.classList.toggle('wide', rows.length > 24);
      barChart.innerHTML = '';
      if (!hasData) return;

      rows.forEach(row => {
        const value = Number(row.value || 0);
        const height = value > 0 ? Math.max(12, Math.round((value / Math.max(max, 1)) * 174)) : 4;
        const item = document.createElement('div');
        item.className = 'profile-bar-item';
        const displayValue = revenue ? numberVN.format(value) : numberVN.format(value);
        const title = revenue ? `${numberVN.format(value)}đ` : `${numberVN.format(value)} dự án`;
        item.innerHTML = `
          <span class="profile-bar-value">${escapeHtml(displayValue)}</span>
          <div class="profile-bar-track"><i style="height:${height}px" title="${escapeHtml(title)}"></i></div>
          <small>${escapeHtml(row.label)}</small>`;
        barChart.appendChild(item);
      });
    };

    const renderPie = data => {
      const slices = Array.isArray(data.pieData) ? data.pieData : [];
      const total = Number(data.pieTotal || slices.reduce((sum, item) => sum + Number(item.count || 0), 0));
      const category = data.pieMode === 'category';

      if (pieDescription) pieDescription.textContent = category
        ? 'Phân bố dự án theo lĩnh vực trong khoảng đã chọn.'
        : 'Phân bố dự án theo trạng thái trong khoảng đã chọn.';
      if (pieTotal) pieTotal.textContent = numberVN.format(total);
      if (pieCenter) pieCenter.textContent = numberVN.format(total);

      const hasData = slices.length > 0 && total > 0;
      if (pieLayout) pieLayout.hidden = !hasData;
      if (pieEmpty) pieEmpty.hidden = hasData;
      if (!hasData) return;

      if (pieChart) pieChart.style.background = data.pieGradient || buildPieGradient(slices);
      if (pieLegend) {
        pieLegend.innerHTML = '';
        slices.forEach((slice, index) => {
          const row = document.createElement('div');
          const dotColor = colors[index % colors.length];
          row.innerHTML = `
            <span><i style="background:${dotColor}"></i><b>${escapeHtml(slice.label)}</b></span>
            <strong><span>${numberVN.format(Number(slice.count || 0))}</span> · <span>${Number(slice.percent || 0).toLocaleString('vi-VN', { minimumFractionDigits: 1, maximumFractionDigits: 1 })}%</span></strong>`;
          pieLegend.appendChild(row);
        });
      }
    };

    const updateRange = data => {
      const fromInput = statsForm.elements.from;
      const toInput = statsForm.elements.to;
      if (fromInput && data.from) fromInput.value = data.from;
      if (toInput && data.to) toInput.value = data.to;
      if (!rangeSummary) return;
      const rangeText = rangeSummary.querySelector('span');
      const daysText = rangeSummary.querySelector('b');
      if (rangeText) rangeText.textContent = `${formatDateVN(data.from)} - ${formatDateVN(data.to)}`;
      if (daysText) daysText.textContent = `${Number(data.days || 0)} ngày`;
    };

    const syncUrl = params => {
      const url = new URL(window.location.href);
      ['from', 'to', 'metric', 'pieMode'].forEach(key => {
        const value = params.get(key);
        if (value) url.searchParams.set(key, value);
        else url.searchParams.delete(key);
      });
      url.hash = 'profile-statistics';
      history.replaceState(null, '', url);
    };

    const loadStatistics = async () => {
      showMessage('');
      const formData = new FormData(statsForm);
      const from = String(formData.get('from') || '');
      const to = String(formData.get('to') || '');
      if (from && to && from > to) {
        showMessage('Từ ngày phải nhỏ hơn hoặc bằng Đến ngày.');
        return;
      }

      const params = new URLSearchParams();
      for (const [key, value] of formData.entries()) {
        if (String(value).trim()) params.set(key, String(value));
      }

      requestController?.abort();
      requestController = new AbortController();
      setLoading(true);
      try {
        const response = await fetch(`${endpoint}?${params.toString()}`, {
          method: 'GET',
          headers: { 'Accept': 'application/json', 'X-Requested-With': 'XMLHttpRequest' },
          signal: requestController.signal,
          credentials: 'same-origin'
        });
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        const data = await response.json();
        renderBars(data);
        renderPie(data);
        updateRange(data);
        params.set('from', data.from);
        params.set('to', data.to);
        params.set('metric', data.metric);
        params.set('pieMode', data.pieMode);
        syncUrl(params);
        showMessage('Đã cập nhật thống kê.', 'success');
        window.setTimeout(() => showMessage(''), 1800);
      } catch (error) {
        if (error?.name !== 'AbortError') {
          showMessage('Không thể tải dữ liệu thống kê. Vui lòng thử lại.');
        }
      } finally {
        setLoading(false);
      }
    };

    statsForm.addEventListener('submit', event => {
      event.preventDefault();
      loadStatistics();
    });

    resetBtn?.addEventListener('click', () => {
      const end = new Date();
      const start = new Date(end);
      start.setDate(end.getDate() - 13);
      statsForm.elements.from.value = localISO(start);
      statsForm.elements.to.value = localISO(end);
      statsForm.elements.metric.value = 'projects';
      statsForm.elements.pieMode.value = 'status';
      loadStatistics();
    });
  }
})();
