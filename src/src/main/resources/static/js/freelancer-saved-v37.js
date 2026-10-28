document.addEventListener('DOMContentLoaded',function(){
  const form=document.querySelector('.tm37-filter form');
  if(!form)return;
  const min=form.querySelector('#saved-min'),max=form.querySelector('#saved-max');
  const radios=form.querySelectorAll('input[name="budgetPreset"]');
  const vals={all:['',''],under5:['','4999999'], '5to15':['5000000','15000000'], '15to30':['15000001','30000000'],over30:['30000001','']};
  radios.forEach(r=>r.addEventListener('change',()=>{if(r.checked){min.value=vals[r.value][0];max.value=vals[r.value][1];}}));
  [min,max].forEach(el=>el.addEventListener('input',()=>{radios.forEach(r=>r.checked=false);}));
  for(const r of radios){const [a,b]=vals[r.value];if(String(min.value)===a&&String(max.value)===b){r.checked=true;break;}r.checked=false;}
  form.addEventListener('submit',function(e){if(min.value&&max.value&&Number(min.value)>Number(max.value)){e.preventDefault();alert('Ngân sách tối thiểu không được lớn hơn tối đa.');}});
});
