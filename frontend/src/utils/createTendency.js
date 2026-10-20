import { getCookie } from './getCookie';

const CREATE_TENDENCY_URL = process.env.REACT_APP_BACKEND_URL + '/tendency';

export const createTendency = async (answers, reasons) => {
    const requestData = {
        userId: getCookie('userId'),
        answers: answers,
        reasons: reasons,
    };

    try {
        const response = await fetch(CREATE_TENDENCY_URL, {
            method: 'POST',
            credentials: 'include',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(requestData),
        });

        return await response; // 설문조사 제출 결과를 반환
    } catch (error) {
        console.error('설문조사 제출 중 에러 발생:', error);
        throw error; // 에러를 다시 던져서 호출하는 쪽에서 처리할 수 있게 함
    }
};
