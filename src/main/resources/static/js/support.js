(() => {
  'use strict';
  const dialog = document.getElementById('support-dialog');
  if (!dialog) return;
  const $ = id => document.getElementById(id);
  let config, busy = false, requestId, selectedAmount;
  const api = async (path, body) => {
    const options = {credentials: 'same-origin'};
    if (body !== undefined) {
      const token = await api('/api/auth/csrf');
      Object.assign(options, {method: 'POST', headers: {'Content-Type': 'application/json', [token.headerName]: token.token}, body: JSON.stringify(body)});
    }
    const response = await fetch(path, options);
    const data = await response.json();
    if (!response.ok) throw new Error(data.error || '요청을 확인할 수 없어요. 다시 시도해주세요.');
    return data;
  };
  const error = message => { $('support-error').textContent = message; $('support-error').hidden = !message; };
  const configure = async () => {
    config = await api('/api/support/orders/config');
    $('support-mode').textContent = !config.available ? '결제 서비스 준비 중' : config.mode === 'TEST' ? '카카오페이 테스트 결제 · 실제 청구 없음' : '선택한 금액이 실제 결제됩니다';
    $('support-start').disabled = !config.available;
  };
  document.querySelectorAll('[data-open-support]').forEach(link => link.addEventListener('click', async event => {
    event.preventDefault(); dialog.showModal(); error('');
    try {await configure();} catch (_) {error('결제 서비스에 연결하지 못했어요.');}
  }));
  $('support-close').addEventListener('click', () => dialog.close());
  $('support-form').addEventListener('submit', async event => {
    event.preventDefault(); if (busy) return; busy = true; $('support-start').disabled = true; error('');
    try {
      await configure(); if (!config.available) throw new Error('결제 서비스 준비 중이에요.');
      const amount = Number(document.querySelector('input[name="supportAmount"]:checked').value);
      if (!requestId || amount !== selectedAmount) {requestId = crypto.randomUUID(); selectedAmount = amount;}
      const order = await api('/api/support/orders', {amount, requestId});
      if (order.status !== 'READY' || order.confirming) throw new Error('이미 진행된 주문이에요. 결제 결과를 확인해주세요.');
      const ready = await api('/api/support/orders/' + order.id + '/ready', {mobile: /Android|iPhone|iPad|iPod/i.test(navigator.userAgent)});
      const redirect = new URL(ready.redirectUrl);
      if (redirect.protocol !== 'https:' || redirect.username || redirect.password ||
          !(/(^|\.)kakao\.com$/.test(redirect.hostname) || /(^|\.)kakaopay\.com$/.test(redirect.hostname)))
        throw new Error('카카오페이 결제 페이지를 확인할 수 없어요.');
      location.assign(redirect.href);
    } catch (failure) {error(failure.message);}
    finally {busy = false; $('support-start').disabled = !config?.available;}
  });
  if (['/support/success', '/support/fail', '/support/cancel'].includes(location.pathname)) {
    const panel = $('support-callback'); panel.hidden = false;
    const title = $('support-callback-title'), copy = $('support-callback-copy'), retry = $('support-retry');
    const params = new URLSearchParams(location.search), orderId = params.get('orderId'), pgToken = params.get('pg_token');
    const validId = /^[a-f0-9-]{36}$/.test(orderId || '');
    if (location.pathname !== '/support/success') {
      title.textContent = location.pathname === '/support/cancel' ? '결제를 취소했어요' : '결제가 완료되지 않았어요'; copy.textContent = '결제창을 닫았거나 결제를 진행할 수 없었어요. 다시 응원하려면 금액을 선택해주세요.';
      history.replaceState(null, '', location.pathname);
    } else {
      const confirm = async () => {
        retry.hidden = true; title.textContent = '결제 승인 확인 중';
        try {
          if (!validId) throw new Error('주문 정보를 확인할 수 없어요.');
          const order = pgToken
            ? await api('/api/support/orders/' + orderId + '/confirm', {pgToken})
            : await api('/api/support/orders/' + orderId + '/reconcile', {});
          if (order.status !== 'SUCCEEDED' || order.provider !== 'KAKAOPAY') throw new Error('아직 승인 완료를 확인하지 못했어요. 새 결제를 시작하지 말고 다시 확인해주세요.');
          title.textContent = order.mode === 'TEST' ? '카카오페이 테스트 결제 완료' : '응원해 주셔서 고마워요 ☕';
          copy.textContent = order.amount.toLocaleString('ko-KR') + '원 · ' + (order.mode === 'TEST' ? '실제 청구 없이 결제사 승인까지 확인했어요.' : '결제사 승인이 완료되었어요.');
          history.replaceState(null, '', '/support/success?orderId=' + encodeURIComponent(orderId));
        } catch (failure) {title.textContent = '승인 결과를 다시 확인해주세요'; copy.textContent = failure.message; retry.hidden = false;}
      };
      retry.addEventListener('click', confirm); confirm();
    }
  }
})();
