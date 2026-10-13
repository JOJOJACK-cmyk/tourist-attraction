const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../../main/resources');
class Element{constructor(){this.attrs={};this.listeners={};this.value='';this.hidden=false;this.disabled=false;this.textContent='';}setAttribute(k,v){this.attrs[k]=v;}addEventListener(k,fn){this.listeners[k]=fn;}focus(){this.focused=true;}async emit(k){await this.listeners[k]?.({preventDefault(){}});}set innerHTML(v){throw Error('Unsafe HTML');}}
const settle=async()=>{for(let i=0;i<8;i++)await new Promise(r=>setImmediate(r));};
async function setup({next='/chat?room=12#messages',search='',signedIn=false,providers={naver:true,google:false},failure=false}={}){
 const html=fs.readFileSync(path.join(root,'templates/login.html'),'utf8'),ids=[...html.matchAll(/id="([^"]+)"/g)].map(m=>m[1]),els=Object.fromEntries(ids.map(id=>[id,new Element()]));const calls=[],assigned=[];let logged=signedIn,token=0,fail=failure,nickname='';
 const ctx=vm.createContext({URL,URLSearchParams,document:{body:{dataset:{next}},getElementById:id=>{assert.ok(els[id],id);return els[id];}},location:{search,origin:'https://travel.example',assign:value=>assigned.push(value)},fetch:async(url,options={})=>{
   calls.push({url,options});let data,ok=true;
   if(fail&&url==='/api/auth/csrf'){ok=false;data={error:'연결을 확인해주세요.'};}
   else if(url==='/api/auth/csrf')data={headerName:'X-CSRF-TOKEN',token:'csrf-'+(++token)};
   else if(url==='/api/auth/me')data={authenticated:logged,admin:false,loginId:'testmember',nickname};
   else if(url==='/api/auth/profile'){const input=JSON.parse(options.body);if(input.nickname==='중복닉'){ok=false;data={error:'이미 사용 중인 닉네임이에요.'};}else{nickname=input.nickname;data={authenticated:true,admin:false,loginId:'testmember',nickname};}}
   else if(url==='/api/auth/social/providers')data=providers;
   else if(url==='/api/auth/signup')data={success:true};
   else if(url==='/api/auth/login'){logged=true;data={success:true};}
   else if(url==='/api/auth/logout'){logged=false;data={success:true};}
   else throw Error(url);
   return {ok,json:async()=>data};
 }});vm.runInContext(fs.readFileSync(path.join(root,'static/js/login.js'),'utf8'),ctx);await settle();return {els,calls,assigned,reconnect(){fail=false;}};
}
(async()=>{
 const f=await setup(),e=f.els;assert.equal(e['auth-submit'].disabled,false);assert.equal(e['auth-confirm'].disabled,true);assert.equal(e['auth-social-naver'].hidden,false);assert.equal(e['auth-social-google'].hidden,true);assert.ok(e['auth-social-naver'].href.endsWith('next=%2Fchat%3Froom%3D12%23messages'));
 await e['auth-tab-signup'].emit('click');assert.equal(e['auth-confirm'].required,true);assert.equal(e['auth-password'].autocomplete,'new-password');
 e['auth-id'].value='testmember';e['auth-password'].value='password123';e['auth-confirm'].value='wrong';await e['auth-form'].emit('submit');assert.equal(f.calls.filter(c=>c.url==='/api/auth/signup').length,0);
 e['auth-confirm'].value='password123';e['auth-signup-nickname'].value='봄여행자';await e['auth-form'].emit('submit');assert.equal(JSON.parse(f.calls.find(c=>c.url==='/api/auth/signup').options.body).nickname,'봄여행자');assert.equal(f.calls.find(c=>c.url==='/api/auth/signup').options.headers['X-CSRF-TOKEN'],'csrf-1');assert.equal(e['auth-confirm'].disabled,true);assert.equal(e['auth-signup-nickname'].disabled,true);assert.equal(e['auth-id'].value,'testmember');assert.equal(e['auth-password'].value,'');assert.ok(e['auth-message'].textContent.includes('회원가입이 완료'));assert.equal(f.assigned.length,0);
 e['auth-password'].value='password123';await e['auth-form'].emit('submit');assert.equal(f.calls.find(c=>c.url==='/api/auth/login').options.headers['X-CSRF-TOKEN'],'csrf-2');assert.equal(f.calls.filter(c=>c.url==='/api/auth/csrf').length,3);assert.equal(f.assigned[0],'/chat?room=12#messages');
 const signed=await setup({signedIn:true});assert.equal(signed.els['auth-entry'].hidden,true);assert.equal(signed.els['auth-signed-in'].hidden,false);
 signed.els['auth-nickname'].value='  여름여행자  ';await signed.els['auth-nickname-form'].emit('submit');const profile=signed.calls.find(c=>c.url==='/api/auth/profile');assert.equal(profile.options.method,'PATCH');assert.equal(profile.options.headers['X-CSRF-TOKEN'],'csrf-1');assert.deepEqual(JSON.parse(profile.options.body),{nickname:'여름여행자'});assert.ok(signed.els['auth-account'].textContent.includes('여름여행자'));assert.equal(signed.assigned.length,0);
 signed.els['auth-nickname'].value='중복닉';await signed.els['auth-nickname-form'].emit('submit');assert.equal(signed.els['auth-nickname'].value,'중복닉');assert.ok(signed.els['auth-error'].textContent.includes('이미 사용'));assert.equal(signed.els['auth-nickname-save'].disabled,false);
 await signed.els['auth-logout'].emit('click');assert.equal(signed.els['auth-entry'].hidden,false);assert.ok(signed.els['auth-message'].textContent.includes('로그아웃'));
 for(const next of ['https://evil.example','//evil.example','/\\evil.example','/api/auth/logout']){const unsafe=await setup({next,signedIn:true});assert.equal(unsafe.els['auth-continue'].href,'/');}
 const unavailable=await setup({providers:{naver:false,google:false},search:'?loginError=unavailable'});assert.equal(unavailable.els['auth-social-naver'].hidden,true);assert.ok(unavailable.els['auth-error'].textContent.includes('아직 연결되지'));
 const retry=await setup({failure:true});assert.equal(retry.els['auth-submit'].disabled,true);assert.equal(retry.els['auth-retry'].hidden,false);retry.reconnect();await retry.els['auth-retry'].emit('click');assert.equal(retry.els['auth-submit'].disabled,false);
 console.log('Login page: mode/validation, signup then login, rotated CSRF, safe original-page navigation, provider availability, signed-in/logout and reconnect passed');
})().catch(e=>{console.error(e);process.exitCode=1});
