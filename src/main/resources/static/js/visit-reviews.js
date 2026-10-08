(() => {
  const get=id=>document.getElementById(id),node=(tag,cls,text)=>{const e=document.createElement(tag);e.className=cls;if(text!==undefined)e.textContent=text;return e;};
  const statusNames={PENDING:'검토 대기',APPROVED:'승인·공개',REJECTED:'반려'},feelings={SATISFIED:'만족',UNSATISFIED:'아쉬움',NEUTRAL:'보통',NOT_USED:'평가 없음'};
  const params=new URLSearchParams(location.search);
  let me={authenticated:false},csrf=null,mode='public',page=0,pages=0,listVersion=0,editing=null,blob=null,uploaded=null;
  const error=e=>{get('visit-error').textContent=e.message||'요청을 처리하지 못했어요.';get('visit-error').hidden=false;};
  const clear=()=>{get('visit-error').hidden=true;get('visit-message').textContent='';};
  async function token(){csrf=await api('/api/auth/csrf');}
  async function api(url,method='GET',body){
    const headers={};if(body&&!(body instanceof FormData))headers['Content-Type']='application/json';
    if(method!=='GET'){if(!csrf)await token();headers[csrf.headerName]=csrf.token;}
    const response=await fetch(url,{method,headers,credentials:'same-origin',body:body instanceof FormData?body:body?JSON.stringify(body):undefined});
    if(response.status===204)return null;
    let data;try{data=await response.json();}catch{throw new Error('서버 응답을 확인하지 못했어요.');}
    if(!response.ok)throw new Error(data.error||'요청을 처리하지 못했어요.');return data;
  }
  async function auth(){
    me=await api('/api/auth/me');get('visit-account-state').textContent=me.authenticated?'로그인했어요 · '+me.loginId:'공개 후기는 누구나 볼 수 있고, 작성은 로그인 후 가능해요.';
    get('visit-account-toggle').textContent=me.authenticated?'로그아웃':'로그인 · 회원가입';get('visit-account-panel').hidden=true;get('visit-tab-admin').hidden=!me.admin;
  }
  function requireLogin(){if(me.authenticated)return;get('visit-account-panel').hidden=false;get('visit-account-panel').scrollIntoView({block:'start'});throw new Error('로그인하고 방문 후기를 남겨주세요.');}
  function selectMode(next){
    if(next!=='public')requireLogin();if(next==='admin'&&!me.admin)throw new Error('관리자 권한이 필요해요.');
    mode=next;page=0;['public','mine','admin'].forEach(k=>get('visit-tab-'+k).setAttribute('aria-pressed',String(k===mode)));
    get('visit-list-title').textContent=mode==='admin'?'방문 후기 검토':mode==='mine'?'내 후기·검토 상태':'공개 방문 후기';
    get('visit-status-label').hidden=mode!=='admin';get('visit-year-label').hidden=mode!=='public';
    get('visit-list-note').textContent=mode==='admin'?'내용을 확인하고 승인하거나 반려 이유를 남겨주세요.':mode==='mine'?'대기·승인·반려 상태와 검토 메모를 확인할 수 있어요. 수정하면 다시 검토를 받아요.':'관리자가 승인한 후기를 익명으로 공개해요.';
  }
  function action(text,fn){const b=node('button','filter',text);b.type='button';b.addEventListener('click',async()=>{clear();b.disabled=true;try{await fn();}catch(e){error(e);}finally{b.disabled=false;}});return b;}
  function card(review){
    const e=node('article','community-post-card visit-card');
    e.append(node('span','card-tag visit-status '+review.status.toLowerCase(),statusNames[review.status]),node('p','small',review.destination+' · 익명 · 방문 '+review.visitedAt+' · 작성 '+review.createdAt.slice(0,10)),node('h3','',review.title));
    const facts=[];if(review.item)facts.push(review.item);if(review.spentWon!==null)facts.push('지출 '+Number(review.spentWon).toLocaleString('ko-KR')+'원');if(review.waitingMinutes!==null)facts.push('대기 '+review.waitingMinutes+'분');if(facts.length)e.append(node('p','visit-facts',facts.join(' · ')));
    const detail=node('details','visit-detail');detail.append(node('summary','','방문 경험 자세히 보기'));
    const assessments=node('ul','visit-assessments');[['식비',review.foodCost],['숙박비',review.lodgingCost],['서비스·응대',review.service],['혼잡·대기',review.crowding]].forEach(([label,value])=>{let text=feelings[value];if(label==='혼잡·대기')text={SATISFIED:'한산·원활',UNSATISFIED:'혼잡·대기 부담',NEUTRAL:'보통·인파 관찰',NOT_USED:'평가 없음'}[value];assessments.append(node('li','',label+' · '+text));});detail.append(assessments);
    [['만족한 점',review.goodPoints],['아쉬운 점',review.badPoints]].forEach(([label,text])=>{if(text){detail.append(node('h4','',label),node('p','community-body',text));}});
    if(review.imageUrl){const image=node('img','community-photo');image.src=review.imageUrl;image.alt='방문 후기에 첨부된 사진';image.loading='lazy';detail.append(image);}
    if(review.reviewNote)detail.append(node('p','visit-review-note','검토 메모 · '+review.reviewNote));
    const buttons=node('div','community-actions');
    if(review.mine)buttons.append(action('수정',()=>editor(review)));
    if(review.mine||me.admin)buttons.append(action('삭제',async()=>{if(!confirm('이 방문 후기를 삭제할까요?'))return;await api('/api/visit-reviews/'+review.id+'?version='+review.version,'DELETE');page=0;await list();get('visit-message').textContent='후기를 삭제했어요.';}));
    if(buttons.children.length)detail.append(buttons);
    if(mode==='admin'){
      detail.open=true;const label=node('label','visit-moderation-note','검토 메모 · 반려 시 필수'),note=node('textarea','');note.maxLength=500;note.rows=2;note.value=review.reviewNote||'';label.append(note);detail.append(label);
      const moderate=async status=>{await api('/api/admin/visit-reviews/'+review.id,'PATCH',{status,note:note.value,version:review.version});await list();get('visit-message').textContent=status==='APPROVED'?'승인했어요. 공개 후기와 종합 화면에 반영돼요.':'반려했어요. 작성자에게 검토 메모가 표시돼요.';};
      const decisions=node('div','community-actions');if(review.status!=='APPROVED')decisions.append(action('승인·공개',()=>moderate('APPROVED')));if(review.status!=='REJECTED')decisions.append(action('반려',()=>moderate('REJECTED')));detail.append(decisions);
    }
    e.append(detail);return e;
  }
  async function list(){
    const version=++listVersion;get('visit-list').setAttribute('aria-busy','true');const query=new URLSearchParams({page:String(page)});
    if(get('visit-filter-destination').value)query.set('destination',get('visit-filter-destination').value);
    if(mode==='public'&&get('visit-filter-year').value)query.set('year',get('visit-filter-year').value);
    if(mode==='admin')query.set('status',get('visit-filter-status').value);
    const endpoint=mode==='admin'?'/api/admin/visit-reviews':mode==='mine'?'/api/visit-reviews/mine':'/api/visit-reviews';
    try{const data=await api(endpoint+'?'+query);if(version!==listVersion)return;pages=data.totalPages;get('visit-list').replaceChildren();
      if(!data.content.length)get('visit-list').append(node('p','local-empty',mode==='admin'?'이 상태의 검토 후기가 없어요.':mode==='mine'?'아직 작성한 방문 후기가 없어요.':'아직 공개된 방문 후기가 없어요. 첫 경험을 남겨보세요.'));
      data.content.forEach(r=>get('visit-list').append(card(r)));get('visit-page-status').textContent=data.totalElements+'건 · '+(pages?page+1:0)+' / '+pages;
      get('visit-prev').disabled=page===0;get('visit-next').disabled=page+1>=pages;
    }finally{if(version===listVersion)get('visit-list').setAttribute('aria-busy','false');}
  }
  function release(){if(blob){URL.revokeObjectURL(blob);blob=null;}uploaded=null;}
  function editor(review=null){
    requireLogin();release();editing=review;get('visit-form').reset();get('visit-editor').hidden=false;get('visit-editor-title').textContent=review?'방문 후기 수정':'직접 방문 후기 작성';get('visit-save').textContent=review?'수정 후 검토 요청':'검토 요청하기';
    get('visit-destination').value=review?.destinationId||get('visit-filter-destination').value;
    if(review){[['visit-date','visitedAt'],['visit-title','title'],['visit-item','item'],['visit-spent','spentWon'],['visit-wait','waitingMinutes'],['visit-food','foodCost'],['visit-lodging','lodgingCost'],['visit-service','service'],['visit-crowding','crowding'],['visit-good','goodPoints'],['visit-bad','badPoints']].forEach(([id,key])=>get(id).value=review[key]??'');}
    get('visit-photo-preview').hidden=!review?.imageUrl;if(review?.imageUrl)get('visit-photo-preview').src=review.imageUrl;
    get('visit-editor').scrollIntoView({block:'start'});
  }
  ['public','mine','admin'].forEach(k=>get('visit-tab-'+k).addEventListener('click',async()=>{clear();try{selectMode(k);await list();}catch(e){error(e);}}));
  get('visit-write').addEventListener('click',()=>{clear();try{editor();}catch(e){error(e);}});
  get('visit-cancel').addEventListener('click',()=>{release();editing=null;get('visit-editor').hidden=true;});
  get('visit-account-toggle').addEventListener('click',async()=>{clear();try{if(me.authenticated){await api('/api/auth/logout','POST');await token();await auth();selectMode('public');release();editing=null;get('visit-editor').hidden=true;await list();}else get('visit-account-panel').hidden=!get('visit-account-panel').hidden;}catch(e){error(e);}});
  get('visit-account-form').addEventListener('submit',async event=>{
    event.preventDefault();clear();const button=event.submitter,type=button?.value||'login';if(button)button.disabled=true;
    try{if(type==='signup'&&get('visit-account-password').value!==get('visit-account-confirm').value)throw new Error('비밀번호 확인이 일치하지 않아요.');
      await api('/api/auth/'+type,'POST',{loginId:get('visit-account-id').value,password:get('visit-account-password').value});
      if(type==='signup')get('visit-message').textContent='회원가입했어요. 로그인하고 후기를 남겨주세요.';
      else{await token();await auth();get('visit-account-form').reset();await list();}
    }catch(e){error(e);}finally{if(button)button.disabled=false;}
  });
  get('visit-photo').addEventListener('change',()=>{
    clear();release();const file=get('visit-photo').files[0];
    if(file&&(!['image/jpeg','image/png'].includes(file.type)||file.size>5*1024*1024)){get('visit-photo').value='';error(new Error('5MB 이하 JPG·PNG 사진을 선택해주세요.'));}
    else if(file){blob=URL.createObjectURL(file);get('visit-photo-preview').src=blob;get('visit-photo-preview').hidden=false;get('visit-remove-photo').checked=false;return;}
    get('visit-photo-preview').hidden=!editing?.imageUrl;if(editing?.imageUrl)get('visit-photo-preview').src=editing.imageUrl;
  });
  get('visit-remove-photo').addEventListener('change',()=>{get('visit-photo-preview').hidden=get('visit-remove-photo').checked||!(blob||editing?.imageUrl);});
  get('visit-form').addEventListener('submit',async event=>{
    event.preventDefault();clear();get('visit-save').disabled=true;
    try{requireLogin();if(Math.max(get('visit-good').value.trim().length,get('visit-bad').value.trim().length)<5)throw new Error('만족한 점 또는 아쉬운 점을 5자 이상 작성해주세요.');
      let imageKey=editing?.imageUrl?.split('/').at(-1)||null;
      if(get('visit-remove-photo').checked)imageKey=null;
      else if(get('visit-photo').files[0]){if(!uploaded){const photo=new FormData();photo.append('file',get('visit-photo').files[0]);uploaded=(await api('/api/community/images','POST',photo)).imageKey;}imageKey=uploaded;}
      const number=id=>get(id).value===''?null:Number(get(id).value);
      const input={destinationId:get('visit-destination').value,visitedAt:get('visit-date').value,title:get('visit-title').value,item:get('visit-item').value,spentWon:number('visit-spent'),waitingMinutes:number('visit-wait'),foodCost:get('visit-food').value,lodgingCost:get('visit-lodging').value,service:get('visit-service').value,crowding:get('visit-crowding').value,goodPoints:get('visit-good').value,badPoints:get('visit-bad').value,imageKey,firsthand:get('visit-firsthand').checked,version:editing?.version??null};
      await api('/api/visit-reviews'+(editing?'/'+editing.id:''),editing?'PUT':'POST',input);
      release();editing=null;get('visit-editor').hidden=true;selectMode('mine');get('visit-filter-destination').value=input.destinationId;await list();get('visit-message').textContent='검토를 요청했어요. 승인되면 공개 후기와 종합 화면에 반영돼요.';
    }catch(e){error(e);}finally{get('visit-save').disabled=false;}
  });
  get('visit-filter').addEventListener('submit',async event=>{event.preventDefault();clear();page=0;try{await list();}catch(e){error(e);}});
  ['visit-filter-destination','visit-filter-year','visit-filter-status'].forEach(id=>get(id).addEventListener('change',async()=>{clear();page=0;try{await list();}catch(e){error(e);}}));
  get('visit-prev').addEventListener('click',async()=>{clear();page=Math.max(0,page-1);try{await list();}catch(e){error(e);}});
  get('visit-next').addEventListener('click',async()=>{clear();if(page+1<pages)page++;try{await list();}catch(e){error(e);}});
  (async()=>{try{
    const korea=new Date(Date.now()+9*60*60*1000).toISOString().slice(0,10);get('visit-date').max=korea;
    for(let year=Number(korea.slice(0,4));year>=2020;year--){const option=node('option','',year+'년');option.value=String(year);get('visit-filter-year').append(option);}
    await token();await auth();const destinations=await api('/api/destinations');destinations.forEach(d=>{const option=node('option','',d.name);option.value=d.slug;get('visit-destination').append(option);get('visit-filter-destination').append(option.cloneNode(true));});
    const selected=params.get('destination');if(destinations.some(d=>d.slug===selected))get('visit-filter-destination').value=selected;
    if([...get('visit-filter-year').options].some(o=>o.value===params.get('year')))get('visit-filter-year').value=params.get('year');
    if(document.body.dataset.moderation==='true'&&me.admin)selectMode('admin');
    await list();if(params.get('write')==='1')editor();
  }catch(e){error(e);}})();
})();
