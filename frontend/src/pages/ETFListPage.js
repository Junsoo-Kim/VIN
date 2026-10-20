import React, { useState, useEffect } from 'react';
import { AppBar, Toolbar, Grid } from '@mui/material';
import Logo from '../components/Logo';
import ETFSearch from '../components/ETFSearch';
import ETFList from '../components/ETFList';
import { getEtfList } from '../utils/getEtfList';
import LogoutButton from '../components/LogoutButton';

process.env.PORT = 3001;

const ETFListPage = () => {
    const [searchTerm, setSearchTerm] = useState(''); // 검색어 상태
    const [priceRange, setPriceRange] = useState([0, 1000000]); // 가격 범위 상태
    const [countries, setCountries] = useState({ KOREA: true, USA: true }); // 국가 체크박스 상태
    const [etfData, setEtfData] = useState([]); // 전체 ETF 데이터
    const [filteredEtfData, setFilteredEtfData] = useState([]); // 1차 가공 리스트
    const [searchKey, setSearchKey] = useState(0); // 페이지를 1로 초기화

    useEffect(() => {
        const fetchEtfData = async () => {
            const etfList = await getEtfList();
            setEtfData(etfList);
            setFilteredEtfData(etfList); // 초기에는 모든 상품 포함
        };

        fetchEtfData();
    }, []);

    const handleSearchTermChange = (term) => {
        setSearchTerm(term);
    };

    const handlePriceRangeChange = (range) => {
        setPriceRange(range);
    };

    const handleCountryChange = (selectedCountries) => {
        setCountries(selectedCountries);
    };

    const handleSearch = () => {
        const filteredData = etfData.filter((etf) => {
            const matchesSearchTerm = etf.longName.toLowerCase().includes(searchTerm.toLowerCase());
            const withinPriceRange = etf.currentPrice >= priceRange[0] && etf.currentPrice <= priceRange[1];
            const matchesCountry = countries[etf.country];
            return matchesSearchTerm && withinPriceRange && matchesCountry;
        });
        setFilteredEtfData(filteredData);
        setSearchKey((prevKey) => prevKey + 1); // 페이지를 1로 초기화
    };

    return (
        <div>
            <AppBar position="static" style={{ backgroundColor: 'transparent', boxShadow: 'none' }}>
                <Toolbar>
                    <Logo />
                    <LogoutButton />
                </Toolbar>
            </AppBar>
            <Grid container style={{ height: '100vh' }}>
                <Grid item xs={12}>
                    <ETFSearch
                        onSearchTermChange={handleSearchTermChange}
                        onPriceRangeChange={handlePriceRangeChange}
                        onSearch={handleSearch}
                        onCountryChange={handleCountryChange}
                    />
                </Grid>
                <Grid item xs={12} style={{ height: 'auto' }}>
                    <ETFList key={searchKey} filteredEtfData={filteredEtfData} />
                </Grid>
            </Grid>
        </div>
    );
};

export default ETFListPage;
