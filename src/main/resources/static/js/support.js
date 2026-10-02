(() => {
  const $=id=>document.getElementById(id),dialog=$('support-dialog');if(!dialog)return;
  let order=null,requestId=null,requestAmount=null,csrf=null,busy=false,opener=null;
  const money=amount=>Number(amount).toLocaleString('ko-KR')+'원';
  async function api(url,method='GET',body){if(method!=='GET')csrf=await api('/api/auth/csrf');const headers={};if(body)headers['Content-Type']='application/json';if(method!=='GET')headers[csrf.headerName]=csrf.token;const response=await fetch(url,{method,headers,credentials:'same-origin',body:body?JSON.stringify(body):undefined});const data=await response.json();if(!response.ok){const failure=new Error(data.error||'모의 후원을 처리하지 못했어요.');failure.status=response.status;throw failure;}return data;}
  function error(e){$('support-error').textContent=e.message||'다시 시도해 주세요.';$('support-error').hidden=false;}
  function setBusy(value){busy=value;['support-close','support-start','support-success','support-cancel','support-fail','support-again'].forEach(id=>$(id).disabled=value);dialog.setAttribute('aria-busy',String(value));if(!value){const focusTarget=!$('support-selection').hidden?$('support-start'):!$('support-checkout').hidden?$('support-success'):$('support-again');focusTarget.focus();}}
  function reset(){order=null;requestId=null;requestAmount=null;$('support-error').hidden=true;$('support-selection').hidden=false;$('support-checkout').hidden=true;$('support-result').hidden=true;}
  function uuid(){if(crypto.randomUUID)return crypto.randomUUID();const bytes=crypto.getRandomValues(new Uint8Array(16));bytes[6]=(bytes[6]&15)|64;bytes[8]=(bytes[8]&63)|128;const h=[...bytes].map(b=>b.toString(16).padStart(2,'0')).join('');return h.slice(0,8)+'-'+h.slice(8,12)+'-'+h.slice(12,16)+'-'+h.slice(16,20)+'-'+h.slice(20);}
  function show(data){
    order=data;$('support-selection').hidden=true;$('support-error').hidden=true;
    if(data.status==='READY'){$('support-checkout').hidden=false;$('support-result').hidden=true;$('support-checkout-amount').textContent=money(data.amount);$('support-success').focus();return;}
    $('support-checkout').hidden=true;$('support-result').hidden=false;
    const outcomes={SUCCEEDED:['☕','응원 체험이 완료됐어요!','따뜻한 마음 고마워요. 실제 금액은 청구되지 않았어요.'],CANCELLED:['↩','모의 후원을 취소했어요.','부담 없이 여행 이야기를 계속 즐겨 주세요.'],FAILED:['☁','모의 후원이 완료되지 않았어요.','실패 상황을 체험했어요. 다시 시작할 수 있어요.'],EXPIRED:['⌛','체험 시간이 지났어요.','새 모의 후원으로 다시 시작해 주세요.']};
    const [icon,title,copy]=outcomes[data.status]||outcomes.FAILED;$('support-result-icon').textContent=icon;$('support-result-title').textContent=title;$('support-result-copy').textContent=copy;$('support-result-amount').textContent='선택한 테스트 금액 · '+money(data.amount);$('support-again').focus();
  }
  document.querySelectorAll('[data-open-support]').forEach(link=>link.addEventListener('click',async e=>{e.preventDefault();opener=link;dialog.showModal();if(order){try{show(await api('/api/support/orders/'+order.id));}catch(errorValue){if(errorValue.status===404)reset();error(errorValue);}}}));
  $('support-close').addEventListener('click',()=>{if(!busy)dialog.close();});dialog.addEventListener('cancel',e=>{if(busy)e.preventDefault();});dialog.addEventListener('close',()=>opener?.focus());
  $('support-form').addEventListener('submit',async e=>{e.preventDefault();if(busy)return;$('support-error').hidden=true;const amount=Number(document.querySelector('input[name="supportAmount"]:checked').value);setBusy(true);try{if(!requestId||requestAmount!==amount){requestId=uuid();requestAmount=amount;}show(await api('/api/support/orders','POST',{amount,requestId}));}catch(errorValue){error(errorValue);}finally{setBusy(false);}});
  async function result(outcome){if(busy||!order)return;setBusy(true);$('support-error').hidden=true;try{show(await api('/api/support/orders/'+order.id+'/result','POST',{outcome}));}catch(e){error(e);try{const latest=await api('/api/support/orders/'+order.id);show(latest);if(latest.status==='READY')error(e);}catch{}}finally{setBusy(false);}}
  $('support-success').addEventListener('click',()=>result('SUCCESS'));$('support-cancel').addEventListener('click',()=>result('CANCEL'));$('support-fail').addEventListener('click',()=>result('FAIL'));$('support-again').addEventListener('click',()=>{if(!busy){reset();$('support-start').focus();}});
})();
