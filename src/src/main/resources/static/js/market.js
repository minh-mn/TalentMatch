(() => {
    'use strict';

    const page = document.querySelector('[data-job-browser]');
    if (!page) return;

    let activeRequest = null;
    let requestSequence = 0;
    let filterTimer = null;

    const byId = id => document.getElementById(id);

    function syncBudgetFields(form) {
        if (!form) return;
        const selected = form.querySelector('input[name="budgetPreset"]:checked');
        const minField = form.querySelector('#minBudgetField');
        const maxField = form.querySelector('#maxBudgetField');
        if (!selected || !minField || !maxField) return;
        minField.value = selected.dataset.budgetMin || '';
        maxField.value = selected.dataset.budgetMax || '';
    }

    function updateSelectedVisuals(form) {
        if (!form) return;
        form.querySelectorAll('.market-filter-group label').forEach(label => {
            const input = label.querySelector('input[type="checkbox"], input[type="radio"]');
            if (input) label.classList.toggle('is-selected', input.checked);
        });
    }

    function cleanUrl(url) {
        const parsed = new URL(url, window.location.origin);
        const removableFalse = new Set(['escrowOnly', 'savedOnly']);
        [...parsed.searchParams.keys()].forEach(key => {
            const values = parsed.searchParams.getAll(key);
            if (!values.length) return;
            if (values.every(value => value.trim() === '')) parsed.searchParams.delete(key);
            if (removableFalse.has(key) && values.every(value => value === 'false')) parsed.searchParams.delete(key);
        });
        return parsed.pathname + (parsed.searchParams.toString() ? `?${parsed.searchParams.toString()}` : '');
    }

    function formUrl(form) {
        syncBudgetFields(form);
        const params = new URLSearchParams();
        const data = new FormData(form);
        for (const [key, value] of data.entries()) {
            if (key === 'budgetPreset' || key === 'page') continue;
            const text = String(value).trim();
            if (!text) continue;
            if ((key === 'escrowOnly' || key === 'savedOnly') && text === 'false') continue;
            params.append(key, value);
        }
        const base = form.getAttribute('action') || '/jobs';
        return cleanUrl(`${base}${params.toString() ? `?${params.toString()}` : ''}`);
    }

    function syncHeaderSearch(url) {
        const parsed = new URL(url, window.location.origin);
        const input = document.querySelector('.market-global-search input[name="q"]');
        if (input) input.value = parsed.searchParams.get('q') || '';
    }

    function copyInputState(currentForm, freshForm) {
        if (!currentForm || !freshForm) return;

        currentForm.querySelectorAll('input[type="hidden"][name]').forEach(current => {
            const name = current.getAttribute('name');
            const fresh = freshForm.querySelector(`input[type="hidden"][name="${CSS.escape(name)}"]`);
            if (fresh) current.value = fresh.value;
        });

        currentForm.querySelectorAll('[data-filter-value][name]').forEach(currentInput => {
            const name = currentInput.getAttribute('name');
            const freshInput = freshForm.querySelector(`[data-filter-value][name="${CSS.escape(name)}"]`);
            if (freshInput && document.activeElement !== currentInput) currentInput.value = freshInput.value;
        });

        currentForm.querySelectorAll('[data-filter-key]').forEach(currentInput => {
            const key = currentInput.dataset.filterKey;
            const freshInput = freshForm.querySelector(`[data-filter-key="${CSS.escape(key)}"]`);
            if (!freshInput) return;

            currentInput.checked = freshInput.checked;
            const currentLabel = currentInput.closest('label');
            const freshLabel = freshInput.closest('label');
            if (!currentLabel || !freshLabel) return;

            currentLabel.classList.toggle('is-selected', freshLabel.classList.contains('is-selected'));
            const currentCount = currentLabel.querySelector('.filter-count');
            const freshCount = freshLabel.querySelector('.filter-count');
            if (currentCount && freshCount) currentCount.textContent = freshCount.textContent;
        });
    }

    function updateStableMarkup(id, freshDoc, preserveHorizontalScroll = false) {
        const current = byId(id);
        const fresh = freshDoc.getElementById(id);
        if (!current || !fresh) return;
        const scrollLeft = preserveHorizontalScroll ? current.scrollLeft : 0;
        current.innerHTML = fresh.innerHTML;
        current.className = fresh.className;
        if (id === 'jobSortMenu') current.removeAttribute('open');
        if (preserveHorizontalScroll) current.scrollLeft = scrollLeft;
    }

    function setBusy(busy) {
        page.classList.toggle('jobs-filtering', busy);
        const results = byId('jobResultsColumn');
        if (!results) return;
        results.classList.toggle('is-loading', busy);
        if (busy) results.setAttribute('aria-busy', 'true');
        else results.removeAttribute('aria-busy');
    }

    function showFilterError(message) {
        let notice = byId('jobAjaxNotice');
        if (!notice) {
            notice = document.createElement('div');
            notice.id = 'jobAjaxNotice';
            notice.className = 'job-ajax-notice';
            const layout = document.querySelector('.market-jobs-layout');
            if (layout) layout.before(notice);
        }
        if (notice) {
            notice.innerHTML = `<i class="fa-solid fa-circle-exclamation"></i><span>${message}</span>`;
            notice.hidden = false;
            clearTimeout(showFilterError.timer);
            showFilterError.timer = setTimeout(() => { notice.hidden = true; }, 3500);
        }
    }

    async function loadJobs(rawUrl, pushState = true) {
        const currentResults = byId('jobResultsColumn');
        if (!currentResults) {
            window.location.assign(rawUrl);
            return;
        }

        const url = cleanUrl(rawUrl);
        const requestId = ++requestSequence;
        if (activeRequest) activeRequest.abort();
        const controller = new AbortController();
        activeRequest = controller;
        setBusy(true);

        try {
            const response = await fetch(url, {
                method: 'GET',
                headers: {
                    'X-Requested-With': 'XMLHttpRequest',
                    'Accept': 'text/html'
                },
                signal: controller.signal,
                credentials: 'same-origin',
                cache: 'no-store'
            });
            if (!response.ok) throw new Error(`HTTP ${response.status}`);

            const html = await response.text();
            const freshDoc = new DOMParser().parseFromString(html, 'text/html');
            const freshResults = freshDoc.getElementById('jobResultsColumn');
            const freshFilter = freshDoc.querySelector('[data-job-filter-form]');

            // Nếu server trả HTML bị cắt giữa chừng thì KHÔNG xóa giao diện đang có.
            if (!freshResults || !freshFilter || !freshDoc.getElementById('jobFilterCard')) {
                throw new Error('Phản hồi danh sách dự án chưa đầy đủ.');
            }
            if (requestId !== requestSequence) return;

            // Chỉ thay nội dung bên trong vùng kết quả để kích thước cột và vị trí cuộn không bị nhảy.
            currentResults.innerHTML = freshResults.innerHTML;

            const currentCount = byId('jobsHeadingCount');
            const freshCount = freshDoc.getElementById('jobsHeadingCount');
            if (currentCount && freshCount) currentCount.textContent = freshCount.textContent;

            updateStableMarkup('jobSortMenu', freshDoc);
            updateStableMarkup('jobCategoryNav', freshDoc, true);
            copyInputState(document.querySelector('[data-job-filter-form]'), freshFilter);

            if (pushState) history.pushState({ jobsAjax: true }, '', url);
            syncHeaderSearch(url);
        } catch (error) {
            if (error.name === 'AbortError') return;
            console.error('TalentMatch job filter:', error);
            showFilterError('Không thể cập nhật bộ lọc. Danh sách hiện tại vẫn được giữ nguyên, hãy thử lại.');
        } finally {
            if (requestId === requestSequence) {
                setBusy(false);
                activeRequest = null;
            }
        }
    }

    // Danh mục, sắp xếp, phân trang và Xóa hết.
    document.addEventListener('click', event => {
        const link = event.target.closest('[data-job-ajax-link]');
        if (!link || !page.contains(link)) return;
        if (link.classList.contains('disabled')) {
            event.preventDefault();
            return;
        }
        const href = link.getAttribute('href');
        if (!href || href === '#' || link.target === '_blank') return;
        event.preventDefault();
        loadJobs(new URL(href, window.location.origin).toString());
    });

    // Checkbox/radio áp dụng ngay. Debounce 180ms để click liên tục không tạo cảm giác giật.
    document.addEventListener('change', event => {
        const form = event.target.closest('[data-job-filter-form]');
        if (!form || !page.contains(form)) return;

        if (event.target.matches('input[name="skill"]')) return;
        if (event.target.matches('input[name="budgetPreset"]')) syncBudgetFields(form);
        updateSelectedVisuals(form);
        clearTimeout(filterTimer);
        filterTimer = setTimeout(() => loadJobs(formUrl(form)), 180);
    });

    // Kỹ năng: chỉ tìm sau khi ngừng gõ, date áp dụng khi thay đổi.
    document.addEventListener('input', event => {
        if (!event.target.matches('[data-job-filter-form] input[name="skill"]')) return;
        const form = event.target.closest('[data-job-filter-form]');
        clearTimeout(filterTimer);
        filterTimer = setTimeout(() => loadJobs(formUrl(form)), 450);
    });

    document.addEventListener('submit', event => {
        const saveForm = event.target.closest('.market-save-cell form');
        if (saveForm && page.contains(saveForm)) {
            const returnField = saveForm.querySelector('input[name="returnTo"]');
            if (returnField) returnField.value = window.location.pathname + window.location.search;
            return;
        }

        const filterForm = event.target.closest('[data-job-filter-form]');
        if (filterForm && page.contains(filterForm)) {
            event.preventDefault();
            clearTimeout(filterTimer);
            loadJobs(formUrl(filterForm));
            return;
        }

        if (event.target.matches('.market-global-search')) {
            event.preventDefault();
            const data = new FormData(event.target);
            const params = new URLSearchParams();
            for (const [key, value] of data.entries()) {
                if (String(value).trim()) params.append(key, value);
            }
            loadJobs(`/jobs${params.toString() ? `?${params.toString()}` : ''}`);
        }
    });

    window.addEventListener('popstate', () => loadJobs(window.location.href, false));

    document.addEventListener('click', event => {
        document.querySelectorAll('[data-sort-menu][open]').forEach(menu => {
            if (!menu.contains(event.target)) menu.removeAttribute('open');
        });
    });
})();
