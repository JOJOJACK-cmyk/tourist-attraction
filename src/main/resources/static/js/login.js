(() => {
  const get=id=>document.getElementById(id),params=new URLSearchParams(location.search);
  // The server validates the destination; also reject non-local values before browser navigation.
  function safeNext(value){try{const url=new URL(value,location.origin);if(!value?.startsWith('/')||value.startsWith('//')||value.includes('\\')||url.origin!==location.origin)return '/';const path=url.pathname;if(!['/','/community','/chat','/visit-reviews','/support','/admin/reviews','/admin/visit-reviews'].includes(path)&&!/^\/destinations\/[a-z-]+$/.test(path))return '/';return url.pathname+url.search+url.hash;}catch{return '/';}}
  const next=safeNext(document.body.dataset.next||'/');let mode='login',csrf=null,ready=false,busy=false;
  const error=e=>{get('auth-error').textContent=e.message||'요청을 처리하지 못했어요.';get('auth-error').hidden=false;};
  const clear=()=>{get('auth-error').hidden=true;get('auth-message').textContent='';};
  function setMode(value){mode=value;const signup=mode==='signup';get('auth-tab-login').setAttribute('aria-selected',String(!signup));get('auth-tab-signup').setAttribute('aria-selected',String(signup));get('auth-panel').setAttribute('aria-labelledby',signup?'auth-tab-signup':'auth-tab-login');get('auth-title').textContent=signup?'회원가입':'로그인';get('auth-description').textContent=signup?'계정을 만들고 여행 경험을 나눠보세요.':'아이디와 비밀번호로 시작해요.';get('auth-confirm-label').hidden=!signup;get('auth-confirm').required=signup;get('auth-confirm').disabled=!signup;get('auth-password').autocomplete=signup?'new-password':'current-password';get('auth-submit').textContent=signup?'회원가입':'로그인';}
  function setBusy(value){busy=value;get('auth-submit').disabled=value||!ready;get('auth-tab-login').disabled=value;get('auth-tab-signup').disabled=value;get('auth-logout').disabled=value;get('auth-retry').disabled=value;if(value)get('auth-submit').textContent='처리 중…';else get('auth-submit').textContent=mode==='signup'?'회원가입':'로그인';}
  async function api(url,method='GET',body){const headers={};if(body)headers['Content-Type']='application/json';if(method!=='GET')headers[csrf.headerName]=csrf.token;const response=await fetch(url,{method,headers,credentials:'same-origin',body:body?JSON.stringify(body):undefined});let data;try{data=await response.json();}catch{throw new Error('서버 응답을 확인하지 못했어요.');}if(!response.ok)throw new Error(data.error||'요청을 처리하지 못했어요.');return data;}
  async function token(){csrf=await api('/api/auth/csrf');}
  function account(me){get('auth-entry').hidden=me.authenticated;get('auth-signed-in').hidden=!me.authenticated;get('auth-account').textContent=me.authenticated?me.loginId+' 계정으로 로그인했어요.':'';get('auth-admin').hidden=!me.admin;get('auth-continue').href=next;}
  async function connect(){ready=false;setBusy(true);get('auth-retry').hidden=true;try{await token();account(await api('/api/auth/me'));ready=true;}catch(e){error(e);get('auth-retry').hidden=false;}finally{setBusy(false);}}
  async function social(){try{const providers=await api('/api/auth/social/providers');['kakao','google'].forEach(p=>{get('auth-social-'+p).href='/auth/social/'+p+'?next='+encodeURIComponent(next);get('auth-social-'+p).hidden=!providers[p];});get('auth-social-note').textContent=providers.kakao||providers.google?'소셜 계정으로도 시작할 수 있어요.':'소셜 로그인은 준비 중이에요. 아이디로 로그인할 수 있어요.';}catch{get('auth-social-note').textContent='소셜 로그인 연결을 확인하지 못했어요. 아이디로 로그인할 수 있어요.';}}
  ['login','signup'].forEach(value=>get('auth-tab-'+value).addEventListener('click',()=>{if(busy)return;clear();setMode(value);}));
  get('auth-form').addEventListener('submit',async event=>{event.preventDefault();if(!ready||busy)return;clear();setBusy(true);try{
    if(mode==='signup'&&get('auth-password').value!==get('auth-confirm').value)throw new Error('비밀번호 확인이 일치하지 않아요.');
    await api('/api/auth/'+mode,'POST',{loginId:get('auth-id').value,password:get('auth-password').value});
    get('auth-password').value='';get('auth-confirm').value='';await token();
    if(mode==='signup'){setMode('login');get('auth-message').textContent='회원가입이 완료됐어요. 같은 아이디로 로그인해주세요.';get('auth-password').focus();}
    else{get('auth-message').textContent='로그인했어요. 원래 화면으로 이동해요.';location.assign(next);}
  }catch(e){error(e);}finally{setBusy(false);}});
  get('auth-logout').addEventListener('click',async()=>{if(!ready||busy)return;clear();setBusy(true);try{await api('/api/auth/logout','POST');await token();account(await api('/api/auth/me'));setMode('login');get('auth-message').textContent='로그아웃했어요.';}catch(e){error(e);}finally{setBusy(false);}});
  get('auth-retry').addEventListener('click',async()=>{clear();await connect();});
  setMode(params.get('mode')==='signup'?'signup':'login');
  (async()=>{await Promise.all([connect(),social()]);const reason=params.get('loginError');if(reason)error(new Error(reason==='unavailable'?'아직 연결되지 않은 소셜 로그인이에요. 아이디로 로그인해주세요.':'소셜 로그인을 완료하지 못했어요. 다시 시도해주세요.'));})();
})();
