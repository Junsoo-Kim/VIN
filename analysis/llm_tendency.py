from langchain_community.chat_models import ChatOpenAI
from langchain.prompts import PromptTemplate

# ChatGPT API 호출을 위한 함수 정의
def evaluate_investment_trait(responses, api_key, user_id, api_base_url=None):
    # 템플릿 정의
    templates = """
    아래 설문조사를 통해 당신의 위험 감수 성향을 평가하겠습니다. 각 문항은 -50에서 50까지의 점수로 평가되며, 점수가 높을수록 공격적, 낮을수록 보수적인 성향을 나타냅니다.

    ### 질문 (선택형 + 서술형)
    1. 당신은 높은 수익을 위해 큰 위험을 감수할 준비가 되어 있습니까? (-50: 전혀 그렇지 않다, -25: 약간 그렇지 않다, 0: 중립적, 25: 약간 그렇다, 50: 매우 그렇다)
       - 선택형 : {answer1}, 서술형: {reason1}
    2. 새로운 투자 기회를 발견했을 때, 빠르게 투자 결정을 내리십니까? (-50: 전혀 그렇지 않다, -25: 약간 그렇지 않다, 0: 중립적, 25: 약간 그렇다, 50: 매우 그렇다)
       - 선택형 : {answer2}, 서술형: {reason2}
    3. 당신은 주식이나 ETF 같은 고위험 상품에 투자하는 것을 선호합니까? (-50: 전혀 그렇지 않다, -25: 약간 그렇지 않다, 0: 중립적, 25: 약간 그렇다, 50: 매우 그렇다)
       - 선택형 : {answer3}, 서술형: {reason3}
    4. 시장의 변동성에 크게 흔들리지 않고 투자를 지속할 수 있습니까? (-50: 전혀 그렇지 않다, -25: 약간 그렇지 않다, 0: 중립적, 25: 약간 그렇다, 50: 매우 그렇다)
       - 선택형 : {answer4}, 서술형: {reason4}
    5. 투자 활동에서 오는 스트레스나 불안 같은 감정에 영향을 받지 않으십니까?  (-50: 전혀 그렇지 않다, -25: 약간 그렇지 않다, 0: 중립적, 25: 약간 그렇다, 50: 매우 그렇다)
       - 선택형 : {answer5}, 서술형: {reason5}
    6. 리스크가 적은 안전한 투자보다는 고위험 고수익 투자에 더 매력을 느끼십니까? (-50: 전혀 그렇지 않다, -25: 약간 그렇지 않다, 0: 중립적, 25: 약간 그렇다, 50: 매우 그렇다)
       - 선택형 : {answer6}, 서술형: {reason6}

    서술형 답변을 -50, -25, 0, 25, 50의 점수로 평가하고, 선택형 점수와 서술형 점수를 합산하여 최종 점수를 도출해주세요.
    서술형 답변 평가 기준:
    - "매우 공격적인 답변" : +50
    - "공격적인 답변" : +25
    - "중립적 답변" : 0
    - "보수적인 답변" : -25
    - "매우 보수적인 답변" : -50


    ### 최종 평가
    모든 문항의 평균 점수를 계산하고, 최종적으로 공격적, 중립적, 보수적 성향으로 분류합니다. 점수 체계는 아래와 같아.
    절대 최종 점수가 -100보다 작거나, 100보다 크면 안돼. 꼭 모든 문항의 평균 점수로 계산했는지 확인해줘.
    보수적 : (점수 : -100 ~ -21)
    중립적 : (점수 : -20 ~ 20)
    공격적 : (점수 : 21 ~ 100)

    ### 결과 형식
    - 코맨트: (6줄로 코맨트를 적기)
    - 최종 점수: (실수로 표현)
    - 투자 성향: (공격적, 중립적, 보수적 중 하나)

    예시:
    - 코맨트: 귀하는 위험을 감수하는 경향이 있으며, 공격적인 투자 성향을 가집니다.
    - 최종 점수: 75.5
    - 투자 성향: 공격적
    """
    
    # 템플릿을 설정하여 프롬프트 생성
    reason_template = PromptTemplate.from_template(templates)
    formatted_prompt = reason_template.format(
        answer1=responses['question1']['answer'],
        reason1=responses['question1']['reason'],
        answer2=responses['question2']['answer'],
        reason2=responses['question2']['reason'],
        answer3=responses['question3']['answer'],
        reason3=responses['question3']['reason'],
        answer4=responses['question4']['answer'],
        reason4=responses['question4']['reason'],
        answer5=responses['question5']['answer'],
        reason5=responses['question5']['reason'],
        answer6=responses['question6']['answer'],
        reason6=responses['question6']['reason']
    )

    # ChatGPT API 호출
    chat_model = ChatOpenAI(openai_api_key=api_key,
                            openai_api_base=api_base_url,
                            max_tokens=2048,
                            model_name='gpt-4o-mini',
                            temperature=0.2)
    response = chat_model.predict(formatted_prompt)

    # 응답에서 최종 점수와 투자 성향 추출
    response_lines = response.split('\n')
    final_score = None
    investment_trait = None
    comment = None

    for line in response_lines:
        if "최종 점수:" in line:
            final_score = line.split(":")[1].strip()
        elif "투자 성향:" in line:
            investment_trait = line.split(":")[1].strip()
        elif "코맨트:" in line:
            comment = line.split(":")[1].strip()

    # 결과를 JSON 형식으로 반환
    return {
        "user_id": user_id,
        "final_score": final_score,
        "investment_trait": investment_trait,
        "comment": comment
    }
