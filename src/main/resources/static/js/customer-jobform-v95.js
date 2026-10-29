/* Trang đăng công việc Client — TalentMatch V95.
   Điều hướng 4 bước, kiểm tra dữ liệu, xem trước và lưu nháp trên trình duyệt. */
(function () {
    'use strict';
    var form = document.getElementById('tmJobForm');
    if (!form) return;

    var panels = Array.from(form.querySelectorAll('.tm-wizard-panel'));
    var stepButtons = Array.from(document.querySelectorAll('[data-step-button]'));
    var backBtn = document.getElementById('tmBack');
    var nextBtn = document.getElementById('tmNext');
    var submitBtn = document.getElementById('tmSubmit');
    var draftBtn = document.getElementById('tmDraft');
    var message = document.getElementById('tmWizardError');
    var note = document.getElementById('tmDraftNote');
    var attachment = document.getElementById('tmAttachment');
    var uploadZone = document.querySelector('.tm-upload-zone');
    var currentStep = 1;
    var isEditing = form.dataset.editing === 'true';
    var account = document.body.dataset.account || 'anonymous';
    var draftKey = 'talentmatch-job-draft-v95:' + account;

    function field(name) {
        return form.elements.namedItem(name);
    }

    function value(name) {
        var input = field(name);
        return input ? String(input.value || '').trim() : '';
    }

    function text(id, content) {
        var element = document.getElementById(id);
        if (element) element.textContent = content;
    }

    function formatBudget(raw) {
        if (!raw || Number(raw) <= 0) return '— đ';
        return new Intl.NumberFormat('vi-VN').format(Number(raw)) + ' đ';
    }

    function selectedText(name) {
        var input = field(name);
        return input && input.selectedOptions && input.selectedOptions.length ? input.selectedOptions[0].textContent.trim() : '—';
    }

    function updatePreview() {
        text('tmPreviewTitle', value('title') || 'Tên công việc của bạn');
        text('tmPreviewCategory', selectedText('category') || 'Danh mục chưa chọn');
        text('tmPreviewCat2', value('category') || '—');
        text('tmPreviewSkills', value('requiredSkills') || 'Kỹ năng chưa nhập');
        text('tmPreviewBudget', formatBudget(value('budget')));
        text('tmPreviewTime', value('estimatedDurationDays') ? value('estimatedDurationDays') + ' ngày' : '—');
        text('tmPreviewType', selectedText('workType'));

        var reviewKeys = ['title', 'category', 'workType', 'budget', 'deadline', 'requiredSkills', 'acceptanceCriteria'];
        reviewKeys.forEach(function (name) {
            var output = form.querySelector('[data-review="' + name + '"]');
            if (!output) return;
            var content = name === 'budget' ? formatBudget(value(name)) :
                ((name === 'workType' || name === 'category') ? selectedText(name) : value(name));
            output.textContent = content || '—';
        });

        ['title', 'description', 'acceptanceCriteria'].forEach(function (name) {
            var counter = document.querySelector('[data-count-for="' + name + '"]');
            if (counter) counter.textContent = String(value(name).length);
        });
    }

    function goToStep(step) {
        currentStep = Math.max(1, Math.min(4, step));
        panels.forEach(function (panel) {
            panel.hidden = Number(panel.dataset.step) !== currentStep;
        });
        stepButtons.forEach(function (button, index) {
            button.classList.toggle('active', index + 1 === currentStep);
            button.classList.toggle('completed', index + 1 < currentStep);
            button.setAttribute('aria-current', index + 1 === currentStep ? 'step' : 'false');
        });
        backBtn.hidden = currentStep === 1;
        nextBtn.hidden = currentStep === 4;
        submitBtn.hidden = currentStep !== 4;
        message.hidden = true;
        updatePreview();
    }

    function validateStep(step) {
        var panel = panels[step - 1];
        var inputs = Array.from(panel.querySelectorAll('input, select, textarea'));
        for (var i = 0; i < inputs.length; i++) {
            var input = inputs[i];
            if (input.type === 'file') continue;
            if (!input.checkValidity()) {
                goToStep(step);
                input.focus();
                input.reportValidity();
                return false;
            }
        }
        if (step === 2 && value('deadline')) {
            var deadline = new Date(value('deadline') + 'T00:00:00');
            if (!isEditing && deadline.getTime() < new Date(new Date().toDateString()).getTime()) {
                goToStep(step);
                message.textContent = 'Hạn hoàn thành không được là ngày trong quá khứ.';
                message.hidden = false;
                return false;
            }
        }
        return true;
    }

    function validateAll() {
        for (var step = 1; step <= 3; step++) {
            if (!validateStep(step)) return false;
        }
        return true;
    }

    nextBtn.addEventListener('click', function () {
        if (validateStep(currentStep)) goToStep(currentStep + 1);
    });
    backBtn.addEventListener('click', function () { goToStep(currentStep - 1); });
    stepButtons.forEach(function (button, index) {
        button.addEventListener('click', function () {
            var target = index + 1;
            if (target > currentStep) {
                for (var step = currentStep; step < target; step++) {
                    if (!validateStep(step)) return;
                }
            }
            goToStep(target);
        });
    });

    // HTML5 validation and invisible wizard panels do not mix; validate each panel in the visible step.
    form.noValidate = true;
    form.addEventListener('submit', function (event) {
        if (!validateAll()) {
            event.preventDefault();
            return;
        }
        if (attachment.files.length) {
            var file = attachment.files[0];
            if (!validateFile(file)) {
                event.preventDefault();
                goToStep(1);
                return;
            }
        }
    });

    function validateFile(file) {
        var ext = '.' + file.name.split('.').pop().toLowerCase();
        if (!['.jpg', '.jpeg', '.png', '.pdf', '.doc', '.docx'].includes(ext) || file.size > 10 * 1024 * 1024) {
            message.textContent = 'Chỉ hỗ trợ JPG, PNG, PDF, DOC hoặc DOCX không quá 10 MB.';
            message.hidden = false;
            return false;
        }
        return true;
    }

    if (attachment) {
        attachment.addEventListener('change', function () {
            var file = this.files.length ? this.files[0] : null;
            text('tmUploadName', file ? file.name : 'Chưa chọn tệp');
            if (file) validateFile(file);
        });
    }
    if (uploadZone && attachment) {
        ['dragenter', 'dragover'].forEach(function (type) {
            uploadZone.addEventListener(type, function (event) {
                event.preventDefault();
                uploadZone.classList.add('dragging');
            });
        });
        ['dragleave', 'drop'].forEach(function (type) {
            uploadZone.addEventListener(type, function (event) {
                event.preventDefault();
                uploadZone.classList.remove('dragging');
            });
        });
        uploadZone.addEventListener('drop', function (event) {
            if (!event.dataTransfer.files.length) return;
            var selected = event.dataTransfer.files[0];
            if (!validateFile(selected)) return;
            var transfer = new DataTransfer();
            transfer.items.add(selected);
            attachment.files = transfer.files;
            attachment.dispatchEvent(new Event('change'));
        });
    }

    document.querySelectorAll('[data-insert]').forEach(function (button) {
        button.addEventListener('click', function () {
            var textarea = field('description');
            var mark = button.dataset.insert;
            var start = textarea.selectionStart;
            var end = textarea.selectionEnd;
            var chosen = textarea.value.substring(start, end);
            var inserted = mark === '• ' ? '\n• ' + chosen : mark + (chosen || 'Nội dung') + mark;
            textarea.setRangeText(inserted, start, end, 'end');
            textarea.dispatchEvent(new Event('input', { bubbles: true }));
            textarea.focus();
        });
    });

    if (!isEditing && draftBtn) {
        draftBtn.addEventListener('click', function () {
            var values = {};
            Array.from(form.querySelectorAll('[name]')).forEach(function (input) {
                if (input.type !== 'file' && input.type !== 'hidden') values[input.name] = input.value;
            });
            try {
                localStorage.setItem(draftKey, JSON.stringify(values));
                note.textContent = 'Đã lưu bản nháp trên trình duyệt này. Tệp đính kèm không được lưu trong bản nháp.';
                note.hidden = false;
            } catch (err) {
                note.textContent = 'Trình duyệt không cho phép lưu bản nháp. Bạn có thể tiếp tục nhập và đăng công việc.';
                note.hidden = false;
            }
        });
        if (!value('title')) {
            try {
                var draft = JSON.parse(localStorage.getItem(draftKey) || 'null');
                if (draft && typeof draft === 'object') {
                    Object.keys(draft).forEach(function (key) {
                        var input = field(key);
                        if (input && input.type !== 'file') input.value = draft[key];
                    });
                    note.textContent = 'Đã khôi phục bản nháp được lưu trên trình duyệt này.';
                    note.hidden = false;
                }
            } catch (err) { /* Không có bản nháp hợp lệ */ }
        }
    }
    form.addEventListener('input', updatePreview);
    form.addEventListener('change', updatePreview);
    goToStep(1);
})();
