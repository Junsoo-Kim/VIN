from airflow import DAG
from airflow.operators.python_operator import PythonOperator
from airflow.utils.dates import days_ago

from recommend import recommend_stocks as recommend_by_correlation

def recommend_stocks(**kwargs):
    input_stock = kwargs['dag_run'].conf.get('input_stock')
    ti = kwargs['ti']

    result = recommend_by_correlation(input_stock)

    # XCom에 결과를 저장
    ti.xcom_push(key='recommendation_result', value=result)
    # 추가 로깅
    print(f"Recommendation result pushed to XCom: {result}")

default_args = {
    'owner': 'airflow',
    'start_date': days_ago(1),
}

dag = DAG(
    'recommend_dag',
    default_args=default_args,
    description='A DAG to recommend stocks based on correlation',
    schedule_interval=None,
)

with dag:
    recommend_task = PythonOperator(
        task_id='recommend_stocks',
        python_callable=recommend_stocks,
        provide_context=True,
    )

    recommend_task
