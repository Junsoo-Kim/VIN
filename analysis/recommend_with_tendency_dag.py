from airflow import DAG
from airflow.operators.python_operator import PythonOperator
from airflow.utils.dates import days_ago

from recommend_with_tendency import customized_recommend_stocks

def recommend_with_tendency(**kwargs):
    input_stock = kwargs['dag_run'].conf.get('input_stock')
    final_score = kwargs['dag_run'].conf.get('final_score')
    ti = kwargs['ti']

    result = customized_recommend_stocks(input_stock, final_score)
    ti.xcom_push(key='recommendation_result', value=result)
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
        python_callable=recommend_with_tendency,
        provide_context=True,
    )

    recommend_tendency_task
