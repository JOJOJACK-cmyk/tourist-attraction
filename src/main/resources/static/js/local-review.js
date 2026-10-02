(() => {
  const get=id=>document.getElementById(id);
  const categories={food:'음식',lodging:'숙박',parking:'주차',transport:'교통',admission:'입장·체험',shopping:'쇼핑',rental:'대여',amenities:'편의시설',other:'기타'};
  const topics={value_satisfaction:'가격 대비 만족',spending_regret:'소비 후회',unexpected_cost:'예상 밖 비용',experience_condition:'이용 조건',satisfying_alternative:'만족한 대안',price_information:'가격 안내',service_quality:'응대·서비스',facility_condition:'시설 상태',access_convenience:'접근·이동',crowding:'혼잡·대기'};
  const sentiments={positive:'만족',negative:'아쉬움',mixed:'의견 혼합',neutral:'참고'};
  const types={direct:'직접 경험',hearsay:'전해 들은 이야기',repost:'공유한 글',unclear:'경험 유형 확인 필요'};
  const make=(tag,cls,text)=>{const el=document.createElement(tag);el.className=cls;if(text!==undefined)el.textContent=text;return el;};
  let running=false,csrf=null;
  async function checkConnection() {
    get('local-reconnect').disabled=true;
    get('local-status').textContent='분석 연결을 확인하고 있어요.';
    try {
      const response=await fetch('/api/reviews/status');if(!response.ok)throw new Error();
      const status=await response.json();
      get('local-status').textContent=status.available?'분석 준비가 됐어요.':status.message;
    } catch {get('local-status').textContent='연결을 확인하지 못했어요. 잠시 후 다시 확인해주세요.';}
    finally {get('local-reconnect').disabled=false;}
  }
  function render(data) {
    const result=get('local-result');result.replaceChildren();
    result.append(make('h3','subheading',data.destination+' · 후기에서 알아둘 점'));
    if(!data.analysis.experiences.length)result.append(make('p','local-empty','이 글에서는 방문 선택에 도움이 되는 구체적인 경험을 찾지 못했어요.'));
    const list=make('div','local-experiences');
    data.analysis.experiences.forEach(item=>{
      const card=make('article','local-experience');
      card.append(make('span','card-tag',categories[item.category]),make('span','small',topics[item.topic]+' · '+sentiments[item.sentiment]+' · '+types[item.experience_type]),make('h3','',item.summary));
      [['장소',item.place],['대상',item.item],['이용 조건',item.condition],['이유',item.reason],['다른 선택',item.alternative],['방문 전 확인',item.check_before_visit]].forEach(([label,value])=>{if(value)card.append(make('p','',label+' · '+value));});
      const support=make('details','');support.append(make('summary','','근거 내용'),make('p','',item.support));
      item.uncertainties.forEach(text=>support.append(make('p','small',text)));
      card.append(support);list.append(card);
    });
    result.append(list);
    if(data.analysis.limitations.length){const notes=make('details','comparison-note');notes.append(make('summary','','분석 메모'));data.analysis.limitations.forEach(text=>notes.append(make('p','small',text)));result.append(notes);}
  }
  get('local-review-form').addEventListener('submit',async event=>{
    event.preventDefault();if(running)return;
    const destinationId=get('destination').value;
    get('local-error').hidden=true;
    if(!destinationId){get('local-error').textContent='위에서 관광지를 먼저 선택해주세요.';get('local-error').hidden=false;return;}
    running=true;get('local-analyze').disabled=true;get('local-analyze').textContent='분석하는 중…';get('local-result').setAttribute('aria-busy','true');get('local-result').replaceChildren();
    get('local-status').textContent='후기를 읽고 있어요. 첫 분석은 모델을 준비하느라 시간이 걸릴 수 있어요.';
    try {
      const tokenResponse=await fetch('/api/auth/csrf');if(!tokenResponse.ok)throw new Error('로그인 상태를 확인해주세요.');
      csrf=await tokenResponse.json();
      const response=await fetch('/api/reviews/extract',{method:'POST',headers:{'Content-Type':'application/json',[csrf.headerName]:csrf.token},body:JSON.stringify({destinationId,body:get('local-body').value,publishedAt:get('local-published').value||null})});
      const data=await response.json();if(!response.ok)throw new Error(data.error||'분석하지 못했어요. 다시 시도해주세요.');
      render(data);get('local-status').textContent='분석이 끝났어요.';
    } catch(error){get('local-error').textContent=error.message;get('local-error').hidden=false;get('local-status').textContent='연결 상태와 후기 내용을 확인해주세요.';}
    finally {running=false;get('local-analyze').disabled=false;get('local-analyze').textContent='후기 분석하기';get('local-result').setAttribute('aria-busy','false');}
  });
  get('local-reconnect').addEventListener('click',checkConnection);
  (async()=>{
    get('local-analyze').disabled=true;
    try {
      const response=await fetch('/api/destinations');if(!response.ok)throw new Error('관광지를 불러오지 못했어요.');
      const destinations=await response.json();get('destination').replaceChildren();
      destinations.forEach(d=>{const option=make('option','',d.name);option.value=d.slug;get('destination').append(option);});
      get('local-analyze').disabled=!destinations.length;
      await checkConnection();
    }catch(error){get('local-error').textContent=error.message;get('local-error').hidden=false;get('local-status').textContent='페이지를 새로고침해주세요.';}
  })();
})();
