/* Hồ sơ doanh nghiệp V102 - hỗ trợ tương tác, không thay thế xác thực server. */
document.addEventListener('DOMContentLoaded', function () {
    const editButton = document.getElementById('companyEditContact');
    const contactFields = document.querySelectorAll('.tm-company-contact-fields input');
    if (editButton) {
        editButton.addEventListener('click', function () {
            const unlock = Array.from(contactFields).some(function (field) { return field.readOnly; });
            contactFields.forEach(function (field) { field.readOnly = !unlock; });
            editButton.innerHTML = unlock ? '<i class="fa-solid fa-check"></i> Đang sửa' : '<i class="fa-solid fa-pen"></i> Sửa';
            if (unlock && contactFields.length) contactFields[0].focus();
            const tip = document.getElementById('companyContactTip');
            if (tip) tip.textContent = unlock ? 'Sau khi sửa, nhấn “Lưu thay đổi” ở mục Thông tin công ty để lưu tất cả thông tin.' : 'Chọn “Sửa” để cập nhật, sau đó nhấn “Lưu thay đổi”.';
        });
    }
    const bio = document.getElementById('bio');
    const length = document.getElementById('companyBioLength');
    if (bio && length) {
        const update = function () { length.textContent = String(bio.value.length); };
        bio.addEventListener('input', update);
        update();
    }
    document.querySelectorAll('input[data-auto-upload]').forEach(function (input) {
        input.addEventListener('change', function () {
            if (!input.files || !input.files.length) return;
            const file = input.files[0];
            const limit = input.dataset.autoUpload === 'document' ? 10000000 : 5000000;
            if (file.size > limit) { alert('Tệp vượt dung lượng cho phép.'); input.value = ''; return; }
            const accepted = input.dataset.autoUpload === 'document' ? /\.pdf$/i.test(file.name) : /\.(png|jpe?g)$/i.test(file.name);
            if (!accepted) { alert('Định dạng tệp không được hỗ trợ.'); input.value = ''; return; }
            if (input.form) input.form.requestSubmit();
        });
    });
});
