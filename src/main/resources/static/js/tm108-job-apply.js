// TalentMatch V108: đếm ký tự, phản hồi lỗi form và tránh gửi ứng tuyển hai lần.
document.addEventListener('DOMContentLoaded', function () {
    var form = document.getElementById('tm108-apply-form');
    if (!form) return;

    var proposal = document.getElementById('tm108-proposal');
    var counter = document.getElementById('tm108-proposal-counter');
    var submit = document.getElementById('tm108-submit');
    var sending = false;

    function updateCount() {
        var count = proposal.value.length;
        counter.textContent = count + ' / 3000';
        counter.classList.toggle('tm108-counter-invalid', count > 0 && count < 30);
    }

    proposal.addEventListener('input', updateCount);
    updateCount();

    form.addEventListener('submit', function (event) {
        if (sending) {
            event.preventDefault();
            return;
        }
        if (!form.checkValidity()) {
            event.preventDefault();
            form.reportValidity();
            return;
        }
        sending = true;
        submit.disabled = true;
        var label = submit.querySelector('span');
        if (label) label.textContent = 'Đang gửi ứng tuyển...';
    });
});
