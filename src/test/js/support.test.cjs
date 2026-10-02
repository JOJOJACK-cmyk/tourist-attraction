const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../../main/resources');
class Element{constructor(){this.listeners={};this.textContent='';this.hidden=true;this.disabled=false;}addEventListener(k,fn){this.listeners[k]=fn;}showModal(){this.open=true;}close(){this.open=false;}}
const id='00000000-0000-4000-8000-000000000001';
function setup(pathname='/support',search='',available=true,approved=true){
 const ids=[...fs.readFileSync(path.join(root,'templates/fragments/support.html'),'utf8').matchAll(/id="([^"]+)"/g),...fs.readFileSync(path.join(root,'templates/support.html'),'utf8').matchAll(/id="([^"]+)"/g)].map(m=>m[1]);
 const els=Object.fromEntries(ids.map(id=>[id,new Element()])),link=new Element(),requests=[],sdk=[],history=[];
 function TossPayments(key){assert.equal(key,'test_ck_example');return {payment:({customerKey})=>{assert.equal(customerKey,'ANONYMOUS');return {requestPayment:async input=>sdk.push(input)};}};} TossPayments.ANONYMOUS='ANONYMOUS';
 const ctx=vm.createContext({window:{TossPayments},location:{pathname,search,origin:'http://localhost:8080'},history:{replaceState:(a,b,url)=>history.push(url)},URLSearchParams,crypto:{randomUUID:()=>id},document:{getElementById:id=>els[id],querySelectorAll:()=>[link],querySelector:()=>({value:'3000'})},fetch:async(url,options)=>{
 requests.push({url,...options});let data;
 if(url.endsWith('/config'))data={available,clientKey:available?'test_ck_example':'',mode:'TEST'};
 else if(url==='/api/auth/csrf')data={headerName:'X-CSRF-TOKEN',token:'csrf'};
 else if(options.method==='POST'){assert.equal(options.headers['X-CSRF-TOKEN'],'csrf');const body=JSON.parse(options.body);data={id,amount:body.amount,currency:'KRW',status:url.endsWith('/confirm')&&approved?'SUCCEEDED':'READY',mode:'TEST'};}
 else data={id,amount:3000,currency:'KRW',status:approved?'SUCCEEDED':'READY',mode:'TEST'};
 return {ok:true,json:async()=>data};}});
 vm.runInContext(fs.readFileSync(path.join(root,'static/js/support.js'),'utf8'),ctx);return {els,link,requests,sdk,history};
}
const flush=()=>new Promise(resolve=>setImmediate(resolve));
(async()=>{
 let s=setup();await s.link.listeners.click({preventDefault(){}});await s.els['support-form'].listeners.submit({preventDefault(){}});
 assert.equal(s.sdk.length,1);assert.equal(s.sdk[0].amount.value,3000);assert.equal(s.sdk[0].orderId,id);assert.equal(s.sdk[0].successUrl,'http://localhost:8080/support/success');assert.equal(s.sdk[0].failUrl,'http://localhost:8080/support/fail');
 assert.equal(s.requests.filter(r=>r.url.endsWith('/confirm')).length,0);assert.equal(s.requests.filter(r=>r.url.endsWith('/result')).length,0);
 s=setup('/support/success',`?orderId=${id}&paymentKey=pg-key&amount=3000`);await flush();
 assert.equal(s.requests.filter(r=>r.url.endsWith('/confirm')).length,1);assert.equal(s.els['support-callback-title'].textContent,'토스 테스트 결제 완료');assert.equal(s.history[0],'/support/success?orderId='+id);
 s=setup('/support/success',`?orderId=${id}&paymentKey=pg-key&amount=3000`,true,false);await flush();assert.equal(s.els['support-retry'].hidden,false);assert.ok(s.els['support-callback-copy'].textContent.includes('새 결제를 시작하지'));
 s=setup('/support', '',false);await s.link.listeners.click({preventDefault(){}});await s.els['support-form'].listeners.submit({preventDefault(){}});assert.equal(s.sdk.length,0);assert.equal(s.els['support-start'].disabled,true);
 s=setup('/support/fail','?message=untrusted');await flush();assert.equal(s.requests.length,0);assert.equal(s.els['support-callback-title'].textContent,'결제가 완료되지 않았어요');
 console.log('Toss UI: SDK request, server confirmation, pending retry, unavailable keys and cancellation passed');
})().catch(e=>{console.error(e);process.exitCode=1});
