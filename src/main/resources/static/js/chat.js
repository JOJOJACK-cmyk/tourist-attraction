(() => {
  const $=id=>document.getElementById(id),el=(tag,cls,text)=>{const n=document.createElement(tag);n.className=cls;if(text!==undefined)n.textContent=text;return n;};
  let me={authenticated:false},csrf=null,room=null,socket=null,retry=null,heartbeat=null,attempt=0,version=0,before=null,joiningToken=new URLSearchParams(location.search).get('invite'),messages=new Map(),loadingOlder=false,sending=false;
  function failure(e){$('chat-error').textContent=e.message||'요청을 처리하지 못했어요.';$('chat-error').hidden=false;}
  function clear(){$('chat-error').hidden=true;$('chat-notice').textContent='';}
  async function api(url,method='GET',body){
    if(method!=='GET'&&!csrf)csrf=await api('/api/auth/csrf');const headers={};if(body)headers['Content-Type']='application/json';if(method!=='GET')headers[csrf.headerName]=csrf.token;
    const response=await fetch(url,{method,headers,credentials:'same-origin',body:body?JSON.stringify(body):undefined});if(response.status===204)return null;
    const data=await response.json();if(!response.ok)throw new Error(data.error||'요청을 처리하지 못했어요.');return data;
  }
  function button(text,fn,cls='filter'){const b=el('button',cls,text);b.type='button';b.addEventListener('click',async()=>{clear();b.disabled=true;try{await fn();}catch(e){failure(e);}finally{b.disabled=false;}});return b;}
  function stop(){++version;clearTimeout(retry);clearInterval(heartbeat);retry=null;heartbeat=null;if(socket){socket.onclose=null;socket.close();socket=null;}attempt=0;}
  function writable(enabled){$('chat-message').disabled=!enabled;$('chat-send').disabled=!enabled||sending;}
  async function auth(){me=await api('/api/auth/me');$('chat-account').textContent=me.authenticated?'로그인했어요 · '+me.loginId:'그룹 채팅방은 로그인 후 이용할 수 있어요.';$('chat-logout').hidden=!me.authenticated;$('chat-login').hidden=me.authenticated;$('chat-workspace').hidden=!me.authenticated;}
  async function list(){const rooms=await api('/api/chat/rooms');$('chat-room-list').replaceChildren();if(!rooms.length)$('chat-room-list').append(el('p','small','아직 참여한 방이 없어요.'));rooms.forEach(r=>{const b=button(r.name,()=>open(r.id),'chat-room-item');b.append(el('span','small',r.memberCount+'명'+(r.closed?' · 닫힌 방':'')));b.setAttribute('aria-pressed',String(room?.id===r.id));$('chat-room-list').append(b);});}
  function renderRoom(){
    $('chat-room-title').textContent=room.name;$('chat-member-count').textContent='참여자 '+room.memberCount+'명';$('chat-close-room').hidden=!room.owner||room.closed;$('chat-copy-invite').hidden=!room.invitePath;$('chat-invite-fallback').hidden=true;
    $('chat-participant-list').replaceChildren();room.participants.forEach(p=>{const row=el('div','chat-participant');row.append(el('span','',p.alias+(p.owner?' · 방장':'')+(p.mine?' · 나':'')));if(room.owner&&!room.closed&&!p.mine)row.append(button('내보내기',async()=>{if(!confirm(p.alias+'님을 내보낼까요?'))return;await api('/api/chat/rooms/'+room.id+'/participants/'+p.id,'DELETE');await refreshRoom();}));$('chat-participant-list').append(row);});
  }
  async function refreshRoom(){const id=room?.id;if(!id)return;const data=await api('/api/chat/rooms/'+id);if(room?.id===id){room=data;renderRoom();await list();}}
  function renderMessages(older=false){
    const box=$('chat-messages'),bottom=box.scrollHeight-box.scrollTop-box.clientHeight<80,oldHeight=box.scrollHeight,oldTop=box.scrollTop;box.replaceChildren();
    [...messages.values()].sort((a,b)=>a.id-b.id).forEach(m=>{const row=el('article','chat-bubble'+(m.mine?' mine':''));row.append(el('p','chat-message-meta',m.alias+(m.mine?' · 나':'')+' · '+m.createdAt.slice(5,10)+' '+m.createdAt.slice(11,16)),el('p','chat-message-body',m.body));box.append(row);});
    if(!messages.size)box.append(el('p','small','첫 여행 이야기를 남겨보세요.'));
    if(older)box.scrollTop=oldTop+box.scrollHeight-oldHeight;else if(bottom||messages.size<=50)box.scrollTop=box.scrollHeight;
  }
  async function history(current,id,older=false){const url='/api/chat/rooms/'+id+'/messages'+(older&&before?'?before='+before:'');const data=await api(url);if(current!==version||room?.id!==id)return;data.messages.forEach(m=>messages.set(m.id,m));before=data.nextBefore;$('chat-older').hidden=!before;renderMessages(older);}
  async function open(id){stop();const current=version;room=null;messages=new Map();before=null;writable(false);$('chat-connection').textContent='방을 불러오는 중이에요.';const data=await api('/api/chat/rooms/'+id);if(current!==version)return;room=data;$('chat-empty').hidden=true;$('chat-active').hidden=false;$('chat-messages').replaceChildren();renderRoom();await list();if(current!==version)return;
    const url=new URL(location.href);url.searchParams.set('room',id);url.searchParams.delete('invite');historyReplace(url);$('chat-invitation').hidden=true;joiningToken=null;
    if(room.closed){$('chat-connection').textContent='닫힌 방이에요. 이전 대화를 볼 수 있어요.';await history(current,id);return;}connect(current,id);
  }
  function historyReplace(url){window.history.replaceState(null,'',url.pathname+url.search);}
  function connect(current,id){
    if(current!==version||room?.closed)return;$('chat-connection').textContent=attempt?'다시 연결하고 있어요…':'실시간 대화에 연결하고 있어요…';writable(false);
    const ws=new WebSocket((location.protocol==='https:'?'wss://':'ws://')+location.host+'/ws/chat?room='+id);socket=ws;
    ws.onmessage=async event=>{if(current!==version||socket!==ws)return;try{const data=JSON.parse(event.data);
      if(data.type==='ready'){attempt=0;$('chat-connection').textContent='실시간 연결됨';writable(true);clearInterval(heartbeat);heartbeat=setInterval(()=>{if(ws.readyState===WebSocket.OPEN)ws.send('ping');},20000);await history(current,id);}
      else if(data.type==='message'){messages.set(data.message.id,data.message);renderMessages();}
      else if(data.type==='members')await refreshRoom();
      else if(data.type==='closed'){room.closed=true;stop();renderRoom();writable(false);$('chat-connection').textContent='방장이 채팅방을 닫았어요.';await list();}
      else if(data.type==='removed'||data.type==='left'){stop();room=null;$('chat-active').hidden=true;$('chat-empty').hidden=false;failure(new Error(data.type==='removed'?'방장이 채팅방에서 내보냈어요.':'채팅방에서 나왔어요.'));await list();}
      else if(data.type==='error')failure(new Error(data.message));
    }catch(e){failure(e);}};
    ws.onclose=()=>{if(current!==version||socket!==ws)return;socket=null;clearInterval(heartbeat);writable(false);$('chat-connection').textContent='연결이 끊겼어요. 다시 연결을 시도해요.';if(attempt++<5)retry=setTimeout(async()=>{try{await refreshRoom();if(room?.closed){$('chat-connection').textContent='닫힌 채팅방이에요.';return;}connect(current,id);}catch(e){failure(e);$('chat-connection').textContent='참여 상태를 확인해주세요.';}},Math.min(1000*2**attempt,30000));else $('chat-connection').textContent='연결되지 않았어요. 방을 다시 선택해주세요.';};
    ws.onerror=()=>{if(current===version) $('chat-connection').textContent='연결 상태를 확인하고 있어요.';};
  }
  async function invitation(token){if(!/^[a-f0-9]{64}$/.test(token||''))throw new Error('초대 링크를 확인해주세요.');const data=await api('/api/chat/invites/'+token);joiningToken=token;$('chat-invitation').hidden=false;$('chat-invite-name').textContent=data.name;$('chat-invite-description').textContent=data.closed?'이미 닫힌 채팅방이에요.':data.memberCount+'명이 함께 이야기하고 있어요.';$('chat-join-form').hidden=data.closed;}
  $('chat-login-form').addEventListener('submit',async e=>{e.preventDefault();clear();const b=e.submitter;if(b)b.disabled=true;try{const signup=b?.value==='signup';if(signup&&$('chat-password').value!==$('chat-confirm').value)throw new Error('비밀번호 확인이 일치하지 않아요.');const credentials={loginId:$('chat-login-id').value,password:$('chat-password').value};if(signup)await api('/api/auth/signup','POST',credentials);await api('/api/auth/login','POST',credentials);csrf=await api('/api/auth/csrf');$('chat-login-form').reset();await auth();await list();if(joiningToken)await invitation(joiningToken);else{const id=Number(new URLSearchParams(location.search).get('room'));if(Number.isSafeInteger(id)&&id>0)await open(id);}}catch(error){failure(error);}finally{if(b)b.disabled=false;}});
  $('chat-logout').addEventListener('click',async()=>{clear();try{stop();await api('/api/auth/logout','POST');csrf=null;room=null;messages.clear();$('chat-active').hidden=true;$('chat-empty').hidden=false;await auth();}catch(e){failure(e);}});
  $('chat-refresh').addEventListener('click',()=>list().catch(failure));
  $('chat-create-form').addEventListener('submit',async e=>{e.preventDefault();clear();$('chat-create-button').disabled=true;try{const data=await api('/api/chat/rooms','POST',{name:$('chat-room-name').value,alias:$('chat-create-alias').value});$('chat-create-form').reset();$('chat-create-panel').open=false;await open(data.id);}catch(error){failure(error);}finally{$('chat-create-button').disabled=false;}});
  $('chat-invite-form').addEventListener('submit',async e=>{e.preventDefault();clear();try{const value=$('chat-invite-input').value.trim(),token=/^[a-f0-9]{64}$/.test(value)?value:new URL(value,location.href).searchParams.get('invite');await invitation(token);}catch(error){failure(error);}});
  $('chat-join-form').addEventListener('submit',async e=>{e.preventDefault();clear();$('chat-join-button').disabled=true;try{const data=await api('/api/chat/invites/'+joiningToken+'/join','POST',{alias:$('chat-join-alias').value});await open(data.id);}catch(error){failure(error);}finally{$('chat-join-button').disabled=false;}});
  $('chat-message-form').addEventListener('submit',async e=>{e.preventDefault();clear();if(sending||!room||room.closed||socket?.readyState!==WebSocket.OPEN)return;const body=$('chat-message').value.trim();if(!body)return;const current=version,id=room.id;sending=true;writable(true);try{const message=await api('/api/chat/rooms/'+id+'/messages','POST',{body});if(current===version){messages.set(message.id,message);$('chat-message').value='';renderMessages();}}catch(error){failure(error);}finally{sending=false;writable(Boolean(room&&!room.closed&&socket?.readyState===WebSocket.OPEN));}});
  $('chat-message').addEventListener('keydown',e=>{if(e.key==='Enter'&&!e.shiftKey&&!e.isComposing){e.preventDefault();$('chat-message-form').requestSubmit();}});
  $('chat-older').addEventListener('click',async()=>{if(loadingOlder||!before||!room)return;loadingOlder=true;$('chat-older').disabled=true;try{await history(version,room.id,true);}catch(e){failure(e);}finally{loadingOlder=false;$('chat-older').disabled=false;}});
  $('chat-copy-invite').addEventListener('click',async()=>{clear();if(!room?.invitePath)return;const link=new URL(room.invitePath,location.origin).href;try{await navigator.clipboard.writeText(link);$('chat-notice').textContent='초대 링크를 복사했어요.';}catch{$('chat-invite-fallback').hidden=false;$('chat-copy-value').value=link;$('chat-copy-value').select();}});
  $('chat-close-room').addEventListener('click',async()=>{if(!room||!confirm('방을 닫으면 새 대화를 보낼 수 없어요. 닫을까요?'))return;clear();try{await api('/api/chat/rooms/'+room.id+'/close','POST');room.closed=true;stop();writable(false);renderRoom();$('chat-connection').textContent='닫힌 채팅방이에요.';await list();}catch(e){failure(e);}});
  $('chat-leave-room').addEventListener('click',async()=>{if(!room||!confirm('채팅방에서 나갈까요?'))return;clear();try{await api('/api/chat/rooms/'+room.id+'/membership','DELETE');stop();room=null;$('chat-active').hidden=true;$('chat-empty').hidden=false;await list();}catch(e){failure(e);}});
  window.addEventListener('beforeunload',stop);
  (async()=>{try{await auth();if(me.authenticated){await list();if(joiningToken)await invitation(joiningToken);else{const id=Number(new URLSearchParams(location.search).get('room'));if(Number.isSafeInteger(id)&&id>0)await open(id);}}}catch(e){failure(e);}})();
})();
