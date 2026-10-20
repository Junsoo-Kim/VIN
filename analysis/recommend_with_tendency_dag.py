from airflow import DAG
from airflow.operators.python_operator import PythonOperator
from airflow.utils.dates import days_ago
from datetime import timedelta  # Add this import

import os
import pandas as pd

# CSV 파일이 저장된 폴더 경로
folder_path = os.path.join(os.path.dirname(__file__), 'correlation')

def customized_recommend_stocks(**kwargs):
    input_stock = kwargs['dag_run'].conf.get('input_stock')
    final_score = kwargs['dag_run'].conf.get('final_score')
    ti = kwargs['ti']
    file_path = os.path.join(folder_path, f"{input_stock}_corr.csv")
    try:
        correlation_data = pd.read_csv(file_path, index_col=0, encoding='utf-8')
    except FileNotFoundError:
        return f"{input_stock}에 대한 파일을 {folder_path}에서 찾을 수 없습니다."
    
    correlation_data_sorted = correlation_data.sort_values(by='Correlation') # 상관계수를 오름차순으로 정렬한다.
    corr_values = correlation_data_sorted['Correlation'].values # 상관계수를 배열로 저장한다.
    etf_list = correlation_data_sorted.index.values # ETF 종목 이름을 배열로 저장한다.
    
    # 고정으로 추천할 ETF 3개 ('공격적', '중립적', '보수적')
    etf_max = etf_list[corr_values.argmax()] # argmax()는 corr_values에서 값이 가장 큰 인덱스 값을 반환한다.
    etf_mid = etf_list[len(corr_values) // 2]
    etf_min = etf_list[corr_values.argmin()] # argmax()는 corr_values에서 값이 가장 작은 인덱스 값을 반환한다.
    
    # 고정으로 추천할 ETF를 튜플로 리스트에 저장한다.
    fixed_recommendations = {
        "보수적 성향 (리스크 헷징 효과가 큰 종목)": [etf_min, corr_values[corr_values.argmin()]],
        "공격적 성향 (리스크 헷징 효과가 약한 종목)": [etf_max, corr_values[corr_values.argmax()]],
        "중립적 성향 (중립적인 투자에 적합한 종목)": [etf_mid, corr_values[len(corr_values) // 2]]
    }

    # 고정으로 추천할 ETF를 리스트에서 제거한다.
    remaining = correlation_data_sorted.drop(index=[etf_max, etf_mid, etf_min], errors='ignore')
    etf_list = remaining.index.values
    corr_values = remaining['Correlation'].values

    # user의 투자 성향을 기반으로 추천할 ETF 2개
    score_range = 200
    ratio = final_score / score_range

    mid_index = len(corr_values) // 2

    if final_score > 40:  # 공격적 투자자
        index = mid_index - int(ratio * mid_index)
        index = max(0, index)
    elif -40 <= final_score <= 40:  # 중립적 투자자
        dynamic_recommendations = {
            "사용자 성향 추천 1": [etf_list[mid_index - 1], corr_values[mid_index - 1]],
            "사용자 성향 추천 2": [etf_list[mid_index + 1], corr_values[mid_index + 1]]
        }
    else:  # 보수적 투자자
        index = mid_index + int(abs(ratio) * mid_index)
        index = min(len(corr_values) - 1, index)

    if final_score != 0:  # 공격적 또는 보수적
        etf_dynamic_2_index = index - 1 if index > 0 else index + 1
        etf_dynamic_2 = etf_list[etf_dynamic_2_index]
    
        if etf_list[index] == etf_dynamic_2:
            dynamic_recommendations = {
                "사용자 성향 추천 1": [etf_list[index], corr_values[index]]
            }
        else:
            dynamic_recommendations = {
                "사용자 성향 추천 1": [etf_list[index], corr_values[index]],
                "사용자 성향 추천 2": [etf_dynamic_2, corr_values[etf_dynamic_2_index]]
            }

    # 최종적으로 추천할 ETF 리스트를 return
    result = {**fixed_recommendations, **dynamic_recommendations}
    ti.xcom_push(key='recommendation_result', value=result)
    print(f"XCom: {ti.xcom_pull(key='recommendation_result', task_ids='recommend_stocks')}")
    # 추가 로깅
    print(f"Recommendation result pushed to XCom: {result}")

default_args = {
    'owner': 'airflow',
    'start_date': days_ago(1),
}

dag = DAG(
    'recommend_with_tendency_dag',
    default_args=default_args,
    description='A DAG to recommend stocks based on user tendency',
    schedule_interval=None,
)

with dag:
    recommend_tendency_task = PythonOperator(
        task_id='recommend_tendency',
        python_callable=customized_recommend_stocks,
        provide_context=True,
    )

    recommend_tendency_task