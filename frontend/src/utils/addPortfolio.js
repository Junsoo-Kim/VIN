import { getCookie } from './getCookie';

const ADD_PORTFOLIO_URL = process.env.REACT_APP_BACKEND_URL + '/portfolio';

export const addPortfolio = async (etfCode, count) => {
    const requestData = {
        userId: getCookie('userId'),
        etfCode: etfCode,
        count: count,
    };

    console.log(requestData);

    try {
        const response = await fetch(ADD_PORTFOLIO_URL, {
            method: 'POST',
            credentials: 'include',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(requestData),
        });

        return await response;
    } catch (error) {
        console.error('포트폴리오 추가 제출 중 에러 발생:', error);
        throw error; // 에러를 다시 던져서 호출하는 쪽에서 처리할 수 있게 함
    }
};
