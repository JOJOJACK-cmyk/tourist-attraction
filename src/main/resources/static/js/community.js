(() => {
  const get=id=>document.getElementById(id);
  const kinds={GENERAL:'자유 게시판',REVIEW:'자유 게시판',REPORT:'자유 게시판',QUESTION:'질문 게시판'};
  const boardKind=kind=>kind==='QUESTION'?'QUESTION':'GENERAL';
  const initialBoard=new URLSearchParams(location.search).get('board')==='question'?'QUESTION':'GENERAL';
  const categories={FOOD:'음식',LODGING:'숙박',PARKING:'주차',TRANSPORT:'교통',ADMISSION:'입장·체험',SHOPPING:'쇼핑',RENTAL:'대여',AMENITIES:'편의시설',OTHER:'기타'};
  let me={authenticated:false},csrf=null,page=0,pages=0,editing=null,detail=null,listVersion=0,detailVersion=0,blob=null;
  const node=(tag,cls,text)=>{const el=document.createElement(tag);el.className=cls;if(text!==undefined)el.textContent=text;return el;};
  const date=value=>value?value.slice(0,10):'';
  const error=e=>{get('board-error').textContent=e.message||'요청을 처리하지 못했어요.';get('board-error').hidden=false;};
  const clear=()=>{get('board-error').hidden=true;get('board-message').textContent='';};
  async function token(){csrf=await api('/api/auth/csrf');}
  async function api(url,method='GET',body){
    const headers={};if(body && !(body instanceof FormData))headers['Content-Type']='application/json';
    if(method!=='GET'){if(!csrf)await token();headers[csrf.headerName]=csrf.token;}
    const response=await fetch(url,{method,headers,body:body instanceof FormData?body:body?JSON.stringify(body):undefined,credentials:'same-origin'});
    if(response.status===204)return null;
    let data;try{data=await response.json();}catch{throw new Error('서버 응답을 확인하지 못했어요.');}
    if(!response.ok)throw new Error(data.error||'요청을 처리하지 못했어요.');return data;
  }
  async function auth(){
    me=await api('/api/auth/me');
    get('account-state').textContent=me.authenticated?'로그인했어요 · '+me.loginId:'글과 댓글은 로그인 후 남길 수 있어요.';
    get('account-toggle').textContent=me.authenticated?'로그아웃':'로그인 · 회원가입';
    get('account-panel').hidden=true;get('hidden-filter-label').hidden=!me.admin;
    if(!me.admin)get('board-hidden').checked=false;
  }
  function requireLogin(){if(me.authenticated)return true;get('account-panel').hidden=false;get('account-panel').scrollIntoView({behavior:'smooth',block:'center'});throw new Error('로그인하고 경험을 나눠보세요.');}
  function action(text,fn,cls='filter'){const button=node('button',cls,text);button.type='button';button.addEventListener('click',async()=>{clear();button.disabled=true;try{await fn();}catch(e){error(e);}finally{button.disabled=false;}});return button;}
  function meta(post){return kinds[post.kind]+' · '+categories[post.category]+(post.destination?' · '+post.destination:'')+' · 익명 · '+date(post.createdAt)+(post.hidden?' · 숨긴 글':'');}
  async function list(){
    const version=++listVersion;get('post-list').setAttribute('aria-busy','true');
    const query=new URLSearchParams({page:String(page)});
    [['destination','board-destination'],['kind','board-kind'],['category','board-category'],['q','board-query']].forEach(([key,id])=>{if(get(id).value)query.set(key,get(id).value);});
    if(me.admin&&get('board-hidden').checked)query.set('includeHidden','true');
    try{
      const data=await api('/api/community/posts?'+query);if(version!==listVersion)return;
      pages=data.totalPages;get('post-list').replaceChildren();
      if(!data.content.length)get('post-list').append(node('p','local-empty',get('board-kind').value==='QUESTION'?'아직 질문이 없어요. 궁금한 여행 정보를 물어보세요.':'아직 글이 없어요. 첫 여행 이야기를 남겨보세요.'));
      data.content.forEach(post=>{const card=node('article','community-post-card');const title=action(post.title,()=>show(post.id),'community-post-title');card.append(node('p','small',meta(post)),title,node('p','community-preview',post.body));get('post-list').append(card);});
      get('page-status').textContent=data.totalElements+'건 · '+(pages?page+1:0)+' / '+pages;
      get('page-prev').disabled=page===0;get('page-next').disabled=page+1>=pages;
    }finally{if(version===listVersion)get('post-list').setAttribute('aria-busy','false');}
  }
  async function show(id){
    const version=++detailVersion;const [post,comments]=await Promise.all([api('/api/community/posts/'+id),api('/api/community/posts/'+id+'/comments')]);
    if(version!==detailVersion)return;detail=post;get('post-detail').hidden=false;get('post-content').replaceChildren();
    const content=get('post-content');content.append(node('p','small',meta(post)),node('h2','',post.title));
    if(post.visitedAt)content.append(node('p','small','방문일 '+post.visitedAt));
    content.append(node('p','community-body',post.body));
    if(post.imageUrl){const image=node('img','community-photo');image.src=post.imageUrl;image.alt='게시글에 첨부된 사진';image.loading='lazy';content.append(image);}
    const buttons=node('div','community-actions');
    if(post.mine)buttons.append(action('수정',()=>editor(post)));
    if(post.mine||me.admin)buttons.append(action('삭제',async()=>{if(!confirm('이 글을 삭제할까요?'))return;await api('/api/community/posts/'+id,'DELETE');detail=null;get('post-detail').hidden=true;page=0;await list();}));
    if(me.admin)buttons.append(action(post.hidden?'다시 공개':'숨기기',async()=>{await api('/api/community/posts/'+id+'/visibility','PATCH',{hidden:!post.hidden});await show(id);await list();}));
    content.append(buttons);get('comment-list').replaceChildren();
    if(!comments.length)get('comment-list').append(node('p','small','첫 댓글을 남겨보세요.'));
    comments.forEach(comment=>{const card=node('article','community-comment');card.append(node('span','small','익명 · '+date(comment.createdAt)),node('p','community-body',comment.body));if(comment.canDelete)card.append(action('댓글 삭제',async()=>{if(!confirm('댓글을 삭제할까요?'))return;await api('/api/community/posts/'+id+'/comments/'+comment.id,'DELETE');await show(id);}));get('comment-list').append(card);});
    get('comment-form').hidden=!me.authenticated||post.hidden;get('comment-body').value='';
    get('post-detail').scrollIntoView({behavior:'smooth',block:'start'});
  }
  function editor(post=null){
    requireLogin();editing=post;get('post-form').reset();get('post-editor').hidden=false;
    get('editor-title').textContent=post?'글 수정하기':kinds[get('board-kind').value]+' 글쓰기';get('save-post').textContent=post?'수정하기':'등록하기';
    get('post-destination').value=post?(post.destinationId||''):get('board-destination').value;
    get('post-kind').value=post?boardKind(post.kind):get('board-kind').value;
    get('post-category').value=post?post.category:'OTHER';
    if(post){get('post-category').value=post.category;get('post-title').value=post.title;get('post-body').value=post.body;get('post-visited').value=post.visitedAt||'';}
    if(blob){URL.revokeObjectURL(blob);blob=null;}
    get('photo-preview').hidden=!post?.imageUrl;if(post?.imageUrl)get('photo-preview').src=post.imageUrl;
    get('post-editor').scrollIntoView({behavior:'smooth',block:'start'});
  }
  function selectBoard(kind){
    get('board-kind').value=kind;
    const question=kind==='QUESTION';
    get('board-general').setAttribute('aria-pressed',String(!question));get('board-question').setAttribute('aria-pressed',String(question));
    get('board-title').textContent=question?'질문 게시판':'자유 게시판';
    get('board-description').textContent=question?'여행지, 먹거리, 가격과 주차까지 궁금한 것을 물어보세요.':'후기, 여행 팁, 소소한 일상까지 편하게 나눠보세요.';
    get('write-post').textContent=question?'질문하기':'글쓰기';
    const url=new URL(location.href);url.searchParams.set('board',question?'question':'free');history.replaceState(null,'',url.pathname+url.search+url.hash);
  }
  [['board-general','GENERAL'],['board-question','QUESTION']].forEach(([id,kind])=>get(id).addEventListener('click',async()=>{
    clear();page=0;selectBoard(kind);++detailVersion;detail=null;get('post-detail').hidden=true;
    try{await list();}catch(e){error(e);}
  }));
  get('account-toggle').addEventListener('click',async()=>{clear();try{if(me.authenticated){await api('/api/auth/logout','POST');await token();await auth();get('post-editor').hidden=true;get('post-detail').hidden=true;detail=null;await list();}else get('account-panel').hidden=!get('account-panel').hidden;}catch(e){error(e);}});
  get('account-form').addEventListener('submit',async event=>{
    event.preventDefault();clear();const button=event.submitter;const type=button?.value||'login';if(button)button.disabled=true;
    try{
      if(type==='signup'&&get('account-password').value!==get('account-confirm').value)throw new Error('비밀번호 확인이 일치하지 않아요.');
      await api('/api/auth/'+type,'POST',{loginId:get('account-id').value,password:get('account-password').value});
      if(type==='signup')get('board-message').textContent='회원가입이 완료됐어요. 로그인해주세요.';
      else{await token();await auth();get('account-form').reset();await list();if(detail)await show(detail.id);}
    }catch(e){error(e);}finally{if(button)button.disabled=false;}
  });
  get('write-post').addEventListener('click',()=>{clear();try{editor();}catch(e){error(e);}});
  get('cancel-post').addEventListener('click',()=>{get('post-editor').hidden=true;editing=null;});
  get('post-photo').addEventListener('change',()=>{
    clear();const file=get('post-photo').files[0];if(blob){URL.revokeObjectURL(blob);blob=null;}
    if(file){if(file.size>5*1024*1024){error(new Error('사진은 5MB 이하로 선택해주세요.'));get('post-photo').value='';get('photo-preview').hidden=!editing?.imageUrl;if(editing?.imageUrl)get('photo-preview').src=editing.imageUrl;return;}blob=URL.createObjectURL(file);get('photo-preview').src=blob;get('photo-preview').hidden=false;get('remove-photo').checked=false;}
    else{get('photo-preview').hidden=!editing?.imageUrl;if(editing?.imageUrl)get('photo-preview').src=editing.imageUrl;}
  });
  get('remove-photo').addEventListener('change',()=>{get('photo-preview').hidden=get('remove-photo').checked||!(blob||editing?.imageUrl);});
  get('post-form').addEventListener('submit',async event=>{
    event.preventDefault();clear();const button=get('save-post');button.disabled=true;
    try{
      requireLogin();let imageKey=editing?.imageUrl?editing.imageUrl.split('/').at(-1):null;
      if(get('remove-photo').checked)imageKey=null;
      else if(get('post-photo').files[0]){const data=new FormData();data.append('file',get('post-photo').files[0]);imageKey=(await api('/api/community/images','POST',data)).imageKey;}
      const input={destinationId:get('post-destination').value,kind:get('post-kind').value,category:get('post-category').value,title:get('post-title').value,body:get('post-body').value,visitedAt:get('post-visited').value||null,imageKey};
      const post=await api('/api/community/posts'+(editing?'/'+editing.id:''),editing?'PUT':'POST',input);
      get('post-editor').hidden=true;editing=null;page=0;selectBoard(boardKind(post.kind));await list();await show(post.id);get('board-message').textContent='이야기를 저장했어요.';
    }catch(e){error(e);}finally{button.disabled=false;}
  });
  get('comment-form').addEventListener('submit',async event=>{event.preventDefault();clear();const button=get('save-comment');button.disabled=true;try{requireLogin();await api('/api/community/posts/'+detail.id+'/comments','POST',{body:get('comment-body').value});await show(detail.id);}catch(e){error(e);}finally{button.disabled=false;}});
  get('board-filter').addEventListener('submit',async event=>{event.preventDefault();clear();page=0;try{await list();}catch(e){error(e);}});
  ['board-destination','board-kind','board-category','board-hidden'].forEach(id=>get(id).addEventListener('change',async()=>{clear();page=0;try{await list();}catch(e){error(e);}}));
  get('page-prev').addEventListener('click',async()=>{clear();page=Math.max(0,page-1);try{await list();}catch(e){error(e);}});
  get('page-next').addEventListener('click',async()=>{clear();if(page+1<pages)page++;try{await list();}catch(e){error(e);}});
  (async()=>{try{
    selectBoard(initialBoard);await token();await auth();const destinations=await api('/api/destinations');
    destinations.forEach(d=>{const option=node('option','',d.name);option.value=d.slug;get('post-destination').append(option);get('board-destination').append(option.cloneNode(true));});
    Object.entries(categories).forEach(([value,label])=>{const option=node('option','',label);option.value=value;get('post-category').append(option);get('board-category').append(option.cloneNode(true));});
    await list();
  }catch(e){error(e);}})();
})();
