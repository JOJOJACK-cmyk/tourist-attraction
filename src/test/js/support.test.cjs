const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../../main/resources');
class Element{constructor(){this.listeners={};this.textContent='';this.hidden=true;this.disabled=false;}addEventListener(k,fn){this.listeners[k]=fn;}showModal(){this.open=true;}close(){this.open=false;}}
const id='00000000-0000-4000-8000-000000000001';
function setup({pathname='/support',search='',available=true,approved=true,mobile=false,redirect='https://online-payment.kakaopay.com/pay'}={}){
 const ids=[...fs.readFileSync(path.join(root,'templates/fragments/support.html'),'utf8').matchAll(/id="([^"]+)"/g),...fs.readFileSync(path.join(root,'templates/support.html'),'utf8').matchAll(/id="([^"]+)"/g)].map(m=>m[1]);
 const els=Object.fromEntries(ids.map(id=>[id,new Element()])),link=new Element(),requests=[],navigation=[],history=[];
 const ctx=vm.createContext({location:{pathname,search,origin:'http://localhost:8080',assign:url=>navigation.push(url)},navigator:{userAgent:mobile?'Android':'Desktop'},history:{replaceState:(a,b,url)=>history.push(url)},URL,URLSearchParams,crypto:{randomUUID:()=>id},document:{getElementById:id=>els[id],querySelectorAll:()=>[link],querySelector:()=>({value:'3000'})},fetch:async(url,options)=>{
 requests.push({url,...options});let data;
 if(url.endsWith('/config'))data={available,provider:'KAKAOPAY',mode:'TEST'};
 else if(url==='/api/auth/csrf')data={headerName:'X-CSRF-TOKEN',token:'csrf'};
 else if(options.method==='POST'){
  assert.equal(options.headers['X-CSRF-TOKEN'],'csrf');const body=JSON.parse(options.body);
  if(url.endsWith('/ready')){assert.equal(body.mobile,mobile);data={orderId:id,redirectUrl:redirect,mode:'TEST'};}
  else if(url.endsWith('/confirm')){assert.deepEqual(body,{pgToken:'pg-token'});data={id,amount:3000,currency:'KRW',status:approved?'SUCCEEDED':'READY',mode:'TEST',provider:'KAKAOPAY'};}
  else if(url.endsWith('/reconcile'))data={id,amount:3000,currency:'KRW',status:approved?'SUCCEEDED':'READY',mode:'TEST',provider:'KAKAOPAY'};
  else {assert.equal(body.amount,3000);data={id,amount:3000,currency:'KRW',status:'READY',mode:'TEST',provider:'KAKAOPAY'};}
 }
 return {ok:true,json:async()=>data};}});
 vm.runInContext(fs.readFileSync(path.join(root,'static/js/support.js'),'utf8'),ctx);return {els,link,requests,navigation,history};
}
const flush=()=>new Promise(resolve=>setImmediate(resolve));
(async()=>{
 for(const mobile of [false,true]){
  let s=setup({mobile});await s.link.listeners.click({preventDefault(){}});await s.els['support-form'].listeners.submit({preventDefault(){}});
  assert.equal(s.navigation[0],'https://online-payment.kakaopay.com/pay');assert.equal(s.requests.filter(r=>r.url.endsWith('/ready')).length,1);assert.equal(s.requests.filter(r=>r.url.endsWith('/confirm')).length,0);
 }
 let s=setup({pathname:'/support/success',search:`?orderId=${id}&pg_token=pg-token`});await flush();
 assert.equal(s.requests.filter(r=>r.url.endsWith('/confirm')).length,1);assert.equal(s.els['support-callback-title'].textContent,'카카오페이 테스트 결제 완료');assert.equal(s.history[0],'/support/success?orderId='+id);
 s=setup({pathname:'/support/success',search:`?orderId=${id}`});await flush();assert.equal(s.requests.filter(r=>r.url.endsWith('/reconcile')).length,1);
 s=setup({pathname:'/support/success',search:`?orderId=${id}&pg_token=pg-token`,approved:false});await flush();assert.equal(s.els['support-retry'].hidden,false);assert.ok(s.els['support-callback-copy'].textContent.includes('새 결제를 시작하지'));
 s=setup({available:false});await s.link.listeners.click({preventDefault(){}});await s.els['support-form'].listeners.submit({preventDefault(){}});assert.equal(s.navigation.length,0);assert.equal(s.els['support-start'].disabled,true);
 s=setup({redirect:'https://kakaopay.com.evil.test/pay'});await s.link.listeners.click({preventDefault(){}});await s.els['support-form'].listeners.submit({preventDefault(){}});assert.equal(s.navigation.length,0);
 for(const pathname of ['/support/fail','/support/cancel']){s=setup({pathname,search:'?message=untrusted'});await flush();assert.equal(s.requests.length,0);assert.ok(!s.els['support-callback-copy'].textContent.includes('untrusted'));}
 console.log('KakaoPay UI: PC/mobile ready redirect, token approval, status reconciliation, pending retry, no keys and cancellation passed');
})().catch(e=>{console.error(e);process.exitCode=1});
