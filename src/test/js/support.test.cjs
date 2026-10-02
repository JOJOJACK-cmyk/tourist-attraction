const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../../main/resources');
class Element{constructor(){this.listeners={};this.value='';this.attrs={};this.textContent='';this.hidden=false;}addEventListener(k,fn){this.listeners[k]=fn;}setAttribute(k,v){this.attrs[k]=v;}focus(){this.focused=true;}showModal(){this.open=true;}close(){this.open=false;this.listeners.close?.();}}
const ids=[...fs.readFileSync(path.join(root,'templates/fragments/support.html'),'utf8').matchAll(/id="([^"]+)"/g)].map(m=>m[1]),els=Object.fromEntries(ids.map(id=>[id,new Element()])),link=new Element(),requests=[];
let serial=0,order=null,csrfVersion=0;
const ctx=vm.createContext({crypto:{randomUUID:()=> '00000000-0000-4000-8000-'+String(++serial).padStart(12,'0')},document:{getElementById:id=>els[id],querySelectorAll:()=>[link],querySelector:()=>({value:'3000'})},fetch:async(url,options)=>{requests.push({url,...options});let data;
 if(url==='/api/auth/csrf')data={headerName:'X-CSRF-TOKEN',token:'token-'+(++csrfVersion)};
 else if(options.method==='POST'){assert.equal(options.headers['X-CSRF-TOKEN'],'token-'+csrfVersion);const body=JSON.parse(options.body);if(url.endsWith('/result')){order.status={SUCCESS:'SUCCEEDED',CANCEL:'CANCELLED',FAIL:'FAILED'}[body.outcome];}else{order={id:'order-'+serial,amount:body.amount,status:'READY',mode:'DEMO'};}data={...order};}
 else data={...order};return {ok:true,status:200,json:async()=>data};}});
vm.runInContext(fs.readFileSync(path.join(root,'static/js/support.js'),'utf8'),ctx);
(async()=>{
 await link.listeners.click({preventDefault(){}});assert.equal(els['support-dialog'].open,true);
 await els['support-form'].listeners.submit({preventDefault(){}});assert.equal(els['support-checkout'].hidden,false);assert.equal(els['support-checkout-amount'].textContent,'3,000원');assert.equal(els['support-success'].disabled,false);
 await els['support-success'].listeners.click();assert.equal(els['support-result'].hidden,false);assert.ok(els['support-result-title'].textContent.includes('완료'));assert.ok(els['support-result-copy'].textContent.includes('실제 금액은 청구되지'));assert.equal(csrfVersion,2);
 els['support-close'].listeners.click();assert.equal(els['support-dialog'].open,false);assert.equal(link.focused,true);
 await link.listeners.click({preventDefault(){}});assert.equal(els['support-result'].hidden,false);
 els['support-again'].listeners.click();await els['support-form'].listeners.submit({preventDefault(){}});await els['support-cancel'].listeners.click();assert.ok(els['support-result-title'].textContent.includes('취소'));
 els['support-again'].listeners.click();await els['support-form'].listeners.submit({preventDefault(){}});await els['support-fail'].listeners.click();assert.ok(els['support-result-title'].textContent.includes('완료되지'));
 assert.equal(requests.filter(r=>r.url==='/api/support/orders').length,3);assert.equal(new Set(requests.filter(r=>r.url==='/api/support/orders').map(r=>JSON.parse(r.body).requestId)).size,3);
 console.log('Support demo UI: amount, CSRF rotation, success/cancel/failure, reopen, new attempt and focus return passed');
})().catch(e=>{console.error(e);process.exitCode=1});
