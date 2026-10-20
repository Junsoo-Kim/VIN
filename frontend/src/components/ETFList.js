import React, { useState, useEffect } from 'react';
import { LineChart } from '@mui/x-charts/LineChart';
import { EtfContentTypography, EtfTitleTypography } from '../styles/typography';
import { ToEtfDetailButton } from '../styles/button';
import { Button, Box, CircularProgress, Typography } from '@mui/material';
import get10ETFInfo from '../utils/get10ETFInfo';
import { useNavigate } from 'react-router-dom';

const ITEMS_PER_PAGE = 10; // 한 페이지에 보여줄 아이템 수

const ETFList = ({ filteredEtfData }) => {
    const [currentPage, setCurrentPage] = useState(1); // 현재 페이지 상태
    const [currentEtfs, setCurrentEtfs] = useState([]); // 현재 페이지에 해당하는 ETF 목록
    const [loading, setLoading] = useState(false); // 로딩 상태
    const navigate = useNavigate();

    const formatAmount = (amount) => {
        return amount.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',');
    };

    useEffect(() => {
        const fetchEtfInfo = async () => {
            setLoading(true);
            const sortedEtfs = filteredEtfData.sort((a, b) => a.longName.localeCompare(b.longName));
            const startIndex = (currentPage - 1) * ITEMS_PER_PAGE;
            const paginatedEtfs = sortedEtfs.slice(startIndex, startIndex + ITEMS_PER_PAGE);
            const etfSymbols = paginatedEtfs.map((etf) => etf.symbol);
            const etfInfo = await get10ETFInfo(etfSymbols);
            setCurrentEtfs(etfInfo);
            setLoading(false);
        };

        fetchEtfInfo();
    }, [filteredEtfData, currentPage]);

    const totalPages = Math.ceil(filteredEtfData.length / ITEMS_PER_PAGE); // 총 페이지 수 계산

    return (
        <div style={{ padding: '16px' }}>
            {loading ? (
                <Box display="flex" justifyContent="center" alignItems="center" height="100vh">
                    <CircularProgress />
                </Box>
            ) : (
                <>
                    <ul style={{ listStyle: 'none', padding: 0 }}>
                        {currentEtfs.map((etf) => (
                            <li
                                key={etf.symbol}
                                style={{
                                    display: 'flex',
                                    flexDirection: 'row',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                    backgroundColor: '#f0f0f0',
                                    border: '1px solid #ddd',
                                    borderRadius: '4px',
                                    padding: '16px',
                                    marginBottom: '10px',
                                    boxShadow: '2px 2px 5px rgba(0, 0, 0, 0.1)',
                                    width: '80%',
                                    margin: '20px auto',
                                }}
                            >
                                <div>
                                    <Typography variant="h6" align="center">
                                        {etf.Country === 'KOREA'
                                            ? '월별 데이터 (21.9 ~ 24.9)'
                                            : '연도별 데이터 (14. ~ 24.)'}
                                    </Typography>
                                    <LineChart
                                        series={[
                                            {
                                                data: etf.chartPrice.map((price) => parseFloat(price)),
                                                color: 'black',
                                            },
                                        ]}
                                        width={500}
                                        height={300}
                                        leftAxis={null}
                                        bottomAxis={null}
                                        axisHighlight={{
                                            x: 'none',
                                            y: 'none',
                                        }}
                                    />
                                </div>
                                <div style={{ margin: '0 200px 0 0', width: '100%' }}>
                                    <EtfTitleTypography style={{ display: 'flex', marginLeft: '10%' }}>
                                        {etf.etfName}
                                    </EtfTitleTypography>
                                    <div style={{ display: 'flex', marginLeft: '10%' }}>
                                        <div>
                                            <EtfContentTypography>
                                                종가{' '}
                                                {formatAmount(etf.etfPrice)}{' '}
                                                원
                                            </EtfContentTypography>
                                            <EtfContentTypography>운용사 {etf.fundmanager}</EtfContentTypography>
                                            <EtfContentTypography>국가 {etf.Country}</EtfContentTypography>
                                        </div>
                                    </div>
                                    <div
                                        style={{
                                            display: 'flex',
                                            justifyContent: 'flex-end',
                                            alignContent: 'flex-end',
                                        }}
                                    >
                                        <ToEtfDetailButton
                                            variant="contained"
                                            onClick={() => navigate(`/etf/detail?etfCode=${etf.symbol}`)}
                                        >
                                            추가하기
                                        </ToEtfDetailButton>
                                    </div>
                                </div>
                            </li>
                        ))}
                    </ul>
                    <Box display="flex" justifyContent="center" mt={2}>
                        <Button
                            variant="contained"
                            onClick={() => setCurrentPage((prev) => Math.max(prev - 1, 1))}
                            disabled={currentPage === 1}
                        >
                            이전
                        </Button>
                        <Box mx={1}>
                            {currentPage} / {totalPages}
                        </Box>
                        <Button
                            variant="contained"
                            onClick={() => setCurrentPage((prev) => Math.min(prev + 1, totalPages))}
                            disabled={currentPage === totalPages}
                        >
                            다음
                        </Button>
                    </Box>
                </>
            )}
        </div>
    );
};

export default ETFList;
