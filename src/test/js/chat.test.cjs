const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../../main/resources');
class Element{constructor(tag='div'){this.tagName=tag;this.children=[];this.value='';this.textContent='';this.listeners={};this.hidden=true;this.attrs={};this.scrollHeight=0;this.scrollTop=0;this.clientHeight=0;}append(...nodes){this.children.push(...nodes);}replaceChildren(...nodes){this.children=nodes;}setAttribute(k,v){this.attrs[k]=v;}addEventListener(k,fn){this.listeners[k]=fn;}reset(){}select(){this.selected=true;}}
const ids=[...fs.readFileSync(path.join(root,'templates/chat.html'),'utf8').matchAll(/id="([^"]+)"/g)].map(m=>m[1]),els=Object.fromEntries(ids.map(id=>[id,new Element()]));
const walk=e=>[e,...e.children.flatMap(walk)],windows={},requests=[],sockets=[];
class Socket{static OPEN=1;constructor(url){this.url=url;this.readyState=1;sockets.push(this);}close(){this.readyState=3;if(this.onclose)this.onclose();}send(){}async emit(data){await this.onmessage({data:JSON.stringify(data)});}}
const room={id:1,name:'주말 여행',closed:false,owner:true,memberCount:1,invitePath:'/chat?invite='+'a'.repeat(64),participants:[{id:1,alias:'방장',mine:true,owner:true}]};
const sample={id:7,body:'<script>문자 그대로</script>',alias:'친구',mine:false,createdAt:'2026-10-02T11:00:00'};
const ctx=vm.createContext({URL,URLSearchParams,WebSocket:Socket,location:{href:'http://localhost/chat?room=1',search:'?room=1',protocol:'http:',host:'localhost',origin:'http://localhost'},navigator:{},window:{history:{replaceState(){}},addEventListener:(k,fn)=>windows[k]=fn},setTimeout,clearTimeout,setInterval,clearInterval,confirm:()=>true,document:{getElementById:id=>{assert.ok(els[id],id);return els[id];},createElement:tag=>new Element(tag)},fetch:async(url,options)=>{
 requests.push({url,...options});let data;
 if(url==='/api/auth/me')data={authenticated:true,loginId:'alice'};
 else if(url==='/api/auth/csrf')data={headerName:'X-CSRF-TOKEN',token:'csrf'};
 else if(url==='/api/chat/rooms')data=[room];
 else if(url==='/api/chat/rooms/1')data=room;
 else if(options.method==='POST'){assert.equal(options.headers['X-CSRF-TOKEN'],'csrf');data={...sample,id:8,body:JSON.parse(options.body).body,mine:true};}
 else if(url.includes('before='))data={messages:[{...sample,id:6,body:'이전 대화'}],nextBefore:null};
 else data={messages:[sample],nextBefore:7};
 return {ok:true,status:200,json:async()=>data};
}});
vm.runInContext(fs.readFileSync(path.join(root,'static/js/chat.js'),'utf8'),ctx);
const settle=async()=>{for(let i=0;i<10;i++)await new Promise(r=>setImmediate(r));};
(async()=>{try{
 await settle();assert.equal(sockets.length,1);assert.equal(sockets[0].url,'ws://localhost/ws/chat?room=1');assert.equal(els['chat-send'].disabled,true);
 await sockets[0].emit({type:'ready'});assert.equal(els['chat-send'].disabled,false);assert.equal(els['chat-connection'].textContent,'실시간 연결됨');
 await sockets[0].emit({type:'message',message:sample});assert.equal(els['chat-messages'].children.length,1);assert.ok(walk(els['chat-messages']).some(e=>e.textContent===sample.body));
 els['chat-message'].value='함께 출발해요';await els['chat-message-form'].listeners.submit({preventDefault(){}});await sockets[0].emit({type:'message',message:{...sample,id:8,body:'함께 출발해요',mine:true}});assert.equal(els['chat-messages'].children.length,2);assert.equal(els['chat-message'].value,'');
 await els['chat-older'].listeners.click();assert.equal(els['chat-messages'].children.length,3);assert.equal(els['chat-older'].hidden,true);
 await els['chat-copy-invite'].listeners.click();assert.equal(els['chat-invite-fallback'].hidden,false);assert.ok(els['chat-copy-value'].value.startsWith('http://localhost/chat?invite='));
 await sockets[0].emit({type:'removed'});assert.equal(els['chat-active'].hidden,true);assert.equal(sockets[0].readyState,3);assert.ok(els['chat-error'].textContent.includes('내보냈어요'));
 console.log('Chat UI: connection state, history/live deduplication, CSRF send, previous history, clipboard fallback, safe text and removal passed');
}finally{windows.beforeunload();}})().catch(e=>{console.error(e);process.exitCode=1});
