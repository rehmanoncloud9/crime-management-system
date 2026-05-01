package com.cms.service;

import com.cms.model.geo.*;
import com.cms.repository.geo.*;

import java.util.List;
import java.util.Optional;

public class GeographyService {

    private final CountryRepository  countryRepo  = new CountryRepository();
    private final ProvinceRepository provinceRepo = new ProvinceRepository();
    private final DistrictRepository districtRepo = new DistrictRepository();
    private final CityRepository     cityRepo     = new CityRepository();
    private final AreaRepository     areaRepo     = new AreaRepository();

    public List<Country>  getAllCountries()                   { return countryRepo.findAll(); }
    public List<Province> getProvincesByCountry(Long id)     { return provinceRepo.findByCountryId(id); }
    public List<District> getDistrictsByProvince(Long id)    { return districtRepo.findByProvinceId(id); }
    public List<City>     getCitiesByDistrict(Long id)       { return cityRepo.findByDistrictId(id); }
    public List<Area>     getAreasByCity(Long id)            { return areaRepo.findByCityId(id); }

    // When keyword is blank, return ALL so dropdowns populate immediately
    public List<Country> searchCountries(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) return countryRepo.findAll();
        return countryRepo.searchByName(keyword.trim());
    }

    public List<Province> searchProvinces(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) return provinceRepo.findAll();
        return provinceRepo.searchByName(keyword.trim());
    }

    public List<District> searchDistricts(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) return districtRepo.findAll();
        return districtRepo.searchByName(keyword.trim());
    }

    public List<City> searchCities(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) return cityRepo.findAll();
        return cityRepo.searchByName(keyword.trim());
    }

    public List<Area> searchAreas(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) return areaRepo.findAll();
        return areaRepo.searchByName(keyword.trim());
    }

    public Optional<District> getDistrictById(Long id) { return districtRepo.findById(id); }
    public Optional<City>     getCityById(Long id)     { return cityRepo.findById(id); }
    public Optional<Area>     getAreaById(Long id)     { return areaRepo.findById(id); }
    public Optional<Country>  getCountryById(Long id)  { return countryRepo.findById(id); }

    public List<City> searchCitiesByDistrict(Long districtId, String keyword) {
        if (districtId == null) return searchCities(keyword);
        List<City> all = cityRepo.findByDistrictId(districtId);
        if (keyword == null || keyword.trim().isEmpty()) return all;
        String kw = keyword.trim().toLowerCase();
        return all.stream().filter(c -> c.getName().toLowerCase().contains(kw)).toList();
    }

    public List<Area> searchAreasByCity(Long cityId, String keyword) {
        if (cityId == null) return searchAreas(keyword);
        List<Area> all = areaRepo.findByCityId(cityId);
        if (keyword == null || keyword.trim().isEmpty()) return all;
        String kw = keyword.trim().toLowerCase();
        return all.stream().filter(a -> a.getName().toLowerCase().contains(kw)).toList();
    }

}