/**************************************************************************
 *  Copyright (C) 2010 Atlas of Living Australia
 *  All Rights Reserved.
 *
 *  The contents of this file are subject to the Mozilla Public
 *  License Version 1.1 (the "License"); you may not use this file
 *  except in compliance with the License. You may obtain a copy of
 *  the License at http://www.mozilla.org/MPL/
 *
 *  Software distributed under the License is distributed on an "AS
 *  IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 *  implied. See the License for the specific language governing
 *  rights and limitations under the License.
 ***************************************************************************/
package au.org.ala.biocache.web;


import au.org.ala.biocache.dao.QidCacheDAO;
import au.org.ala.biocache.dao.SearchDAO;
import au.org.ala.biocache.dto.*;
import au.org.ala.biocache.util.QueryFormatUtils;
import com.ctc.wstx.util.URLUtil;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.opengis.metadata.identification.CharacterSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;

import javax.inject.Inject;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Controller for the "explore your area" page
 *
 * @author "Nick dos Remedios <Nick.dosRemedios@csiro.au>"
 * @author "Natasha Carter <natasha.carter@csiro.au>"
 */
@Controller("exploreController")
public class ExploreController {

    /**
     * Logger initialisation
     */
    private final static Logger logger = Logger.getLogger(ExploreController.class);

    /**
     * Fulltext search DAO
     */
    @Inject
    protected SearchDAO searchDao;
    /**
     * Name of view for site home page
     */
    private final String POINTS_GEOJSON = "json/pointsGeoJson";

    @Inject
    protected QidCacheDAO qidCacheDao;

    @Value("${species.subgroups.url:/data/biocache/config/subgroups.json}")
    protected String speciesSubgroupsUrl;
    private String speciesSubgroups = null;

    public String getSubgroupsConfig() {
        if (speciesSubgroups == null) {
            speciesSubgroups = getStringFromPath(speciesSubgroupsUrl);
        }

        return speciesSubgroups;
    }

    @Value("${species.groups.url:/data/biocache/config/groups.json}")
    protected String speciesGroupsUrl;
    private String speciesGroups = null;

    public String getGroupsConfig() {
        if (speciesGroups == null) {
            speciesGroups = getStringFromPath(speciesGroupsUrl);
        }

        return speciesGroups;
    }

    private String getStringFromPath(String path) {
        String result = null;
        try {
            if (path.startsWith("http")) {
                result = StreamUtils.copyToString(URLUtil.inputStreamFromURL(new URL(path)), CharacterSet.UTF_8.toCharset());
            } else {
                result = FileUtils.readFileToString(new File(path), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            logger.error("Exception reading from species.subgroups.url: " + speciesSubgroups, e);
        }
        return result;
    }

    /**
     * Mapping of radius in km to OpenLayers zoom level
     */
    public final static HashMap<Float, Integer> radiusToZoomLevelMap = new HashMap<Float, Integer>();

    static {
        radiusToZoomLevelMap.put(1f, 14);
        radiusToZoomLevelMap.put(5f, 12);
        radiusToZoomLevelMap.put(10f, 11);
        radiusToZoomLevelMap.put(50f, 9);
    }

    @RequestMapping(value = "/explore/hierarchy", method = RequestMethod.GET)
    public void getHierarchy(HttpServletResponse response) throws Exception {
        response.setContentType("application/json");
        try {
            OutputStream out = response.getOutputStream();
            out.write(getSubgroupsConfig().getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.close();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    /**
     * Returns a hierarchical listing of species groups.
     *
     * TODO push down to service implementation.
     *
     * @param requestParams
     * @return
     * @throws Exception
     */
    @RequestMapping(value = "/explore/hierarchy/groups*", method = RequestMethod.GET)
    public @ResponseBody Collection<SpeciesGroupDTO> yourHierarchicalAreaView(
            SpatialSearchRequestParams requestParams, String speciesGroup) throws Exception {

        JSONArray ssgs = JSONArray.fromObject(getSubgroupsConfig());
        Map<String, SpeciesGroupDTO> parentGroupMap = new LinkedHashMap<String, SpeciesGroupDTO>();

        //create a parent lookup table
        Map<String, String> parentLookup = new HashMap<String, String>();
        for (Object sg : ssgs) {
            if (sg instanceof JSONObject
                    && ((JSONObject) sg).containsKey("parent")
                    && ((JSONObject) sg).containsKey("name")) {
                String parent = ((JSONObject) sg).getString("parent");
                if (StringUtils.isNotEmpty(parent)) {
                    parentLookup.put(((JSONObject) sg).getString("name").toLowerCase(), parent);
                    if (parentGroupMap.get(parent) == null) {
                        parentGroupMap.put(parent, new SpeciesGroupDTO(parent, 0, 0, 1));
                    }
                }
            }
        }

        //get the species group occurrence counts
        requestParams.setFormattedQuery(null);
        requestParams.setFacets(new String[]{});
        requestParams.setPageSize(0);
        requestParams.setFlimit(-1);
        if (StringUtils.isNotBlank(speciesGroup)) {
            requestParams.setFq(new String[]{OccurrenceIndex.SPECIES_GROUP + ":\"" + speciesGroup + "\""});
        }

        //retrieve a list of subgroups with occurrences matching the query
        SearchResultDTO speciesSubgroupCounts = searchDao.findByFulltextSpatialQuery(requestParams, null);
        Map<String, Long> occurrenceCounts = new HashMap<String, Long>();
        if (speciesSubgroupCounts.getFacetResults().size() > 0) {
            FacetResultDTO result = speciesSubgroupCounts.getFacetResults().iterator().next();
            for (FieldResultDTO fr : result.getFieldResult()) {
                occurrenceCounts.put(fr.getLabel(), fr.getCount());
            }
        }

        String taxonName = OccurrenceIndex.TAXON_NAME;

        //do a facet query for each species subgroup
        for (String ssg : occurrenceCounts.keySet()) {

            requestParams.setQ(OccurrenceIndex.SPECIES_SUBGROUP + ":\"" + ssg + "\"");
            requestParams.setFormattedQuery(null);
            requestParams.setFacets(new String[]{taxonName});

            List<FacetResultDTO> facetResultDTO = searchDao.getFacetCounts(requestParams);
            if (facetResultDTO.size() > 0) {
                FacetResultDTO result = facetResultDTO.get(0);

                String parentName = parentLookup.get(ssg.toLowerCase());
                SpeciesGroupDTO parentGroup = parentGroupMap.get(parentName);
                if (parentGroup != null) {
                    if (parentGroup.getChildGroups() == null) {
                        parentGroup.setChildGroups(new ArrayList<SpeciesGroupDTO>());
                    }
                    parentGroup.getChildGroups().add(new SpeciesGroupDTO(ssg, result.getCount(), occurrenceCounts.get(ssg), 2));
                    parentGroup.setSpeciesCount(parentGroup.getSpeciesCount() + result.getCount());
                    parentGroup.setCount(parentGroup.getCount() + occurrenceCounts.get(ssg));
                } else {
                    logger.warn("Parent group lookup failed for: " + parentName + ", ssg: " + ssg);
                }
            }
        }

        //prune empty parents
        List<String> toRemove = new ArrayList<String>();
        for(String parentName: parentGroupMap.keySet()){
            if(parentGroupMap.get(parentName).getChildGroups()==null || parentGroupMap.get(parentName).getChildGroups().size()==0){
                toRemove.add(parentName);
            }
        }
        for(String key: toRemove){
            parentGroupMap.remove(key);
        }

        return parentGroupMap.values();
    }

    /**
     *
     * Returns a list of species groups and counts that will need to be displayed.
     *
     * TODO: MOVE all the IP lat long lookup to the the client webapp.  The purposes
     * of biocache-service is to provide a service layer over the biocache.
     *
     */
    @RequestMapping(value = "/explore/groups*", method = RequestMethod.GET)
	public @ResponseBody List<SpeciesGroupDTO> yourAreaView(SpatialSearchRequestParams requestParams) throws Exception {

        //now we want to grab all the facets to get the counts associated with the species groups
        JSONArray sgs = JSONArray.fromObject(getGroupsConfig());
        List<SpeciesGroupDTO> speciesGroups = new java.util.ArrayList<SpeciesGroupDTO>();
        SpeciesGroupDTO all = new SpeciesGroupDTO();
        String[] originalFqs = requestParams.getFq();
        all.setName("ALL_SPECIES");
        all.setLevel(0);
        Integer[] counts = getYourAreaCount(requestParams, "ALL_SPECIES");
        all.setCount(counts[0]);
        all.setSpeciesCount(counts[1]);
        speciesGroups.add(all);

        String oldName = null;
        String kingdom = null;
        //set the counts an indent levels for all the species groups
        for (Object sg : sgs) {
            if (sg instanceof JSONObject
                    && ((JSONObject) sg).containsKey("name")) {

                String parent = null;
                if (((JSONObject) sg).containsKey("parent")){
                    parent = ((JSONObject) sg).getString("parent");
                }

                String name = ((JSONObject) sg).getString("name");

                if (logger.isDebugEnabled()) {
                    logger.debug("name: " + name + " parent: " + parent);
                }

                int level = 3;
                SpeciesGroupDTO sdto = new SpeciesGroupDTO();
                sdto.setName(name);

                if (oldName != null && parent != null && parent.equals(kingdom)) {
                    level = 2;
                }

                oldName = name;
                if (StringUtils.isBlank(parent) || "null".equalsIgnoreCase(parent)) {
                    level = 1;
                    kingdom = name;
                }
                sdto.setLevel(level);
                //set the original query back to default to clean up after ourselves
                requestParams.setFq(originalFqs);
                //query per group
                counts = getYourAreaCount(requestParams, name);
                sdto.setCount(counts[0]);
                sdto.setSpeciesCount(counts[1]);
                speciesGroups.add(sdto);
            }
        }
        return speciesGroups;
    }

    /**
     * Returns the number of records and distinct species in a particular species group
     *
     * @param requestParams
     * @param group
     * @return
     * @throws Exception
     */
    @RequestMapping(value = "/explore/counts/group/{group}*", method = RequestMethod.GET)
    public @ResponseBody
    Integer[] getYourAreaCount(SpatialSearchRequestParams requestParams,
                               @PathVariable(value = "group") String group) throws Exception {
        String taxonName = OccurrenceIndex.TAXON_NAME;

        addGroupFilterToQuery(requestParams, group);
        requestParams.setPageSize(0);
        requestParams.setFacets(new String[]{taxonName});
        requestParams.setFlimit(-1);
        SearchResultDTO results = searchDao.findByFulltextSpatialQuery(requestParams, null);
        Integer speciesCount = 0;
        if (results.getFacetResults().size() > 0) {
            List<FieldResultDTO> fieldResults = results.getFacetResults().iterator().next().getFieldResult();
            int count = 0;
            for (FieldResultDTO fr : fieldResults) {
                if (fr.getCount() != 0 && !fr.getLabel().equalsIgnoreCase("Unknown")) {
                    count++;
                }
            }
            speciesCount = count;
        }

        return new Integer[]{(int) results.getTotalRecords(), speciesCount};
    }

    /**
     * Updates the requestParams to take into account the provided species group
     *
     * @param requestParams
     * @param group
     */
    private void addGroupFilterToQuery(SpatialSearchRequestParams requestParams, String group) {
        addFacetFilterToQuery(requestParams, OccurrenceIndex.SPECIES_GROUP, group);
    }

    /**
     * Updates the requestParams to take into account the provided species group
     *
     * @param requestParams
     * @param facetValue
     */
    private void addFacetFilterToQuery(SpatialSearchRequestParams requestParams, String facetName, String facetValue) {
        String rankId = OccurrenceIndex.TAXON_RANK_ID;

        List<String> fqs = new ArrayList<>();

        if (!facetValue.equals("ALL_SPECIES")) {
            fqs.add(facetName + ":" + facetValue);
        }
        // TODO:PIPELINE
        fqs.add(rankId + ":[" + 7000 + " TO *]");

        new QueryFormatUtils().addFqs(fqs.toArray(new String[0]), requestParams);

        //don't care about the formatted query
        requestParams.setFormattedQuery(null);
    }

    /**
     * GeoJSON view of records as clusters of points within a specified radius of a given location
     *
     * This service will be used by explore your area.
     */
    @RequestMapping(value = "/geojson/radius-points", method = RequestMethod.GET)
        public String radiusPointsGeoJson(SpatialSearchRequestParams requestParams,
            @RequestParam(value="zoom", required=false, defaultValue="0") Integer zoomLevel,
            @RequestParam(value="bbox", required=false) String bbox,
            @RequestParam(value="group", required=false, defaultValue="ALL_SPECIES") String speciesGroup,
            Model model)
            throws Exception {
        addGroupFilterToQuery(requestParams, speciesGroup);
        PointType pointType = PointType.POINT_00001; // default value for when zoom is null
        pointType = getPointTypeForZoomLevel(zoomLevel);
        logger.info("PointType for zoomLevel ("+zoomLevel+") = "+pointType.getLabel());
        List<OccurrencePoint> points = searchDao.findRecordsForLocation(requestParams, pointType);
        logger.info("Points search for "+pointType.getLabel()+" - found: " + points.size());
        model.addAttribute("points", points);
        return POINTS_GEOJSON;
    }

    /**
     * Map a zoom level to a coordinate accuracy level
     *
     * @param zoomLevel
     * @return
     */
    protected PointType getPointTypeForZoomLevel(Integer zoomLevel) {
        PointType pointType = null;
        // Map zoom levels to lat/long accuracy levels
        if (zoomLevel != null) {
            if (zoomLevel >= 0 && zoomLevel <= 6) {
                // 0-6 levels
                pointType = PointType.POINT_1;
            } else if (zoomLevel > 6 && zoomLevel <= 8) {
                // 6-7 levels
                pointType = PointType.POINT_01;
            } else if (zoomLevel > 8 && zoomLevel <= 10) {
                // 8-9 levels
                pointType = PointType.POINT_001;
            } else if (zoomLevel > 10 && zoomLevel <= 13) {
                // 10-12 levels
                pointType = PointType.POINT_0001;
            } else if (zoomLevel > 13 && zoomLevel <= 15) {
                // 12-n levels
                pointType = PointType.POINT_00001;
            } else {
                // raw levels
                pointType = PointType.POINT_RAW;
            }
        }
        return pointType;
    }

    private void applyFacetForCounts(SpatialSearchRequestParams requestParams, boolean useCommonName) {
        if (useCommonName)
            requestParams.setFacets(new String[]{OccurrenceIndex.COMMON_NAME_AND_LSID});
        else
            requestParams.setFacets(new String[]{OccurrenceIndex.NAMES_AND_LSID});
    }

    /**
     * Occurrence search page uses SOLR JSON to display results
     *
     * @return
     * @throws Exception
     */
    @RequestMapping(value = "/explore/group/{group}/download*", method = RequestMethod.GET)
    public void yourAreaDownload(
            DownloadRequestParams requestParams,
            @PathVariable(value="group") String group,
            @RequestParam(value="common", required=false, defaultValue="false") boolean common,
            HttpServletResponse response)
            throws Exception {
	    String filename = requestParams.getFile() != null ? requestParams.getFile():"data";
        logger.debug("Downloading the species in your area... ");
        response.setHeader("Cache-Control", "must-revalidate");
        response.setHeader("Pragma", "must-revalidate");
        response.setHeader("Content-Disposition", "attachment;filename="+filename);
        response.setContentType("application/vnd.ms-excel");

        addGroupFilterToQuery(requestParams, group);
        applyFacetForCounts(requestParams, common);

        try {
            ServletOutputStream out = response.getOutputStream();
            int count = searchDao.writeSpeciesCountByCircleToStream(requestParams, group, out);
            logger.debug("Exported " + count + " species records in the requested area");
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

	}

    /**
     * JSON web service that returns a list of species and record counts for a given location search
     * and a higher taxa with rank.
     *
     * @param model
     * @throws Exception
     */
    @RequestMapping(value = "/explore/group/{group}*", method = RequestMethod.GET)
	public @ResponseBody List<TaxaCountDTO> listSpeciesForHigherTaxa(
            SpatialSearchRequestParams requestParams,
            @PathVariable(value="group") String group,
            @RequestParam(value="common", required=false, defaultValue="false") boolean common,
            Model model) throws Exception {


        addGroupFilterToQuery(requestParams, group);
        applyFacetForCounts(requestParams, common);

        return searchDao.findAllSpeciesByCircleAreaAndHigherTaxa(requestParams, group);
    }
	// The Endemism Web Services - Move these if they get too large...
	/**
	 * Returns the number of distinct species that are in the supplied region.
	 * @param requestParams
	 * @param response
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/explore/counts/endemic*", method = RequestMethod.GET)
	public @ResponseBody int getSpeciesCountOnlyInWKT(SpatialSearchRequestParams requestParams,
	          HttpServletResponse response)
	          throws Exception{
	    List list = getSpeciesOnlyInWKT(requestParams,response);
	    if(list != null)
	        return list.size();
	    return 0;
	}

	/**
	 * Returns the species that only have occurrences in the supplied WKT.
	 * @return
	 */
	@RequestMapping(value = "/explore/endemic/species*", method = RequestMethod.GET)
	public @ResponseBody List<FieldResultDTO> getSpeciesOnlyInWKT(SpatialSearchRequestParams requestParams,
	          HttpServletResponse response)
	              throws Exception{
	    Qid qid = qidCacheDao.getQidFromQuery(requestParams.getQ());
	    String wkt =StringUtils.isNotBlank(requestParams.getWkt())?requestParams.getWkt():qid.getWkt();
	    if(qid != null){
	        requestParams.setQ(qid.getQ());
	        requestParams.setWkt(qid.getWkt());
            requestParams.setFq(qid.getFqs());
	    }

	    if(StringUtils.isNotBlank(wkt) ){
	        if(requestParams.getFacets() != null && requestParams.getFacets().length ==1){
                return searchDao.getEndemicSpecies(requestParams);
	        } else {
	            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Please supply only one facet.");
	        }
	    } else {
	        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Please supply a WKT area.");
	    }
	    return null;
	}

    /**
     * Returns facet values that only occur in the supplied subQueryQid
     * and not in the parentQuery.
     *
     * The facet is defined in the parentQuery. Default facet is SearchDAOImpl.NAMES_AND_LSID
     *
     * If no requestParams defined the default q=*:* is used.
     *
     * @return
     */
    @RequestMapping(value = "/explore/endemic/species/{subQueryQid}*", method = RequestMethod.GET)
    public @ResponseBody List<FieldResultDTO> getSpeciesOnlyInOneQuery(SpatialSearchRequestParams parentQuery,
                                                  @PathVariable(value = "subQueryQid") Long subQueryQid,
                                                  HttpServletResponse response)
            throws Exception {
        SpatialSearchRequestParams subQuery = new SpatialSearchRequestParams();
        subQuery.setQ("qid:" + subQueryQid);

        if (parentQuery.getQ() == null) {
            parentQuery.setQ("*:*");
        }
        if (parentQuery.getFacets() == null || parentQuery.getFacets().length == 0) {
            parentQuery.setFacets(new String[]{OccurrenceIndex.NAMES_AND_LSID});
        }

        if (subQuery != null) {
            if (parentQuery.getFacets() != null && parentQuery.getFacets().length == 1) {
                return searchDao.getSubquerySpeciesOnly(subQuery, parentQuery);
            } else {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Please supply only one facet.");
            }
        } else {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Please supply a valid sub query qid.");
        }

        return null;
    }

    /**
     * Returns count of facet values that only occur in the supplied subQueryQid
     * and not in the parentQuery.
     *
     * The facet is defined in the parentQuery. Default facet is SearchDAOImpl.NAMES_AND_LSID
     *
     * If no requestParams defined the default q=*:* is used.
     *
     * @return
     */
    @RequestMapping(value = "/explore/endemic/speciescount/{subQueryQid}*", method = RequestMethod.GET)
    public @ResponseBody Map getSpeciesOnlyInOneCountQuery(SpatialSearchRequestParams parentQuery,
                                                                       @PathVariable(value="subQueryQid") Long subQueryQid,
                                                                       HttpServletResponse response)
            throws Exception{
        List items = getSpeciesOnlyInOneQuery(parentQuery, subQueryQid, response);

        Map m = null;

        if (items != null) {
            m = new HashMap();
            m.put("count", items.size());
        }

        return m;
    }

    /**
     * Returns the species that only have occurrences in the supplied WKT.
     *
     * @return
     */
    @RequestMapping(value = "/explore/endemic/species.csv", method = RequestMethod.GET)
    public void getEndemicSpeciesCSV(SpatialSearchRequestParams requestParams, HttpServletResponse response) throws Exception {
        String speciesGuid = OccurrenceIndex.SPECIESID;
        requestParams.setFacets(new String[]{OccurrenceIndex.NAMES_AND_LSID});
        requestParams.setFq((String[]) ArrayUtils.add(requestParams.getFq(), speciesGuid + ":[* TO *]"));
        List<FieldResultDTO> list = getSpeciesOnlyInWKT(requestParams, response);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/plain");
        java.io.PrintWriter writer = response.getWriter();
        writer.write("Family,Scientific name,Common name,Taxon rank,LSID,# Occurrences");
        for (FieldResultDTO item : list) {
            String s = item.getLabel();
            if (s.startsWith("\"") && s.endsWith("\"") && s.length() > 2) s = s.substring(1, s.length() - 1);
            String[] values = s.split("\\|", 6);
            if (values.length >= 5) {
                writer.write("\n" + values[4] + ",\"" + values[0] + "\",\"" + values[2] + "\",," + values[1] + "," + item.getCount());
            }
        }
        writer.flush();
        writer.close();
    }

    /**
     * Returns facet values that only occur in the supplied subQueryQid
     * and not in the parentQuery.
     *
     * The facet is defined in the parentQuery. Default facet is SearchDAOImpl.NAMES_AND_LSID
     *
     * If no requestParams defined the default q=*:* is used.
     *
     * @return
     */
    @RequestMapping(value = "/explore/endemic/species/{subQueryQid}.csv", method = RequestMethod.GET)
    public void getSpeciesOnlyInOneQueryCSV(SpatialSearchRequestParams parentQuery,
                                            @PathVariable(value = "subQueryQid") Long subQueryQid,
                                            @RequestParam(value = "count", required = false, defaultValue = "false") boolean includeCount,
                                            @RequestParam(value = "lookup", required = false, defaultValue = "false") boolean lookupName,
                                            @RequestParam(value = "synonym", required = false, defaultValue = "false") boolean includeSynonyms,
                                            @RequestParam(value = "lists", required = false, defaultValue = "false") boolean includeLists,
                                            @RequestParam(value = "file", required = false, defaultValue = "") String file,
                                            HttpServletResponse response)
            throws Exception {
        SpatialSearchRequestParams subQuery = new SpatialSearchRequestParams();
        subQuery.setQ("qid:" + subQueryQid);

        if (parentQuery.getQ() == null) {
            parentQuery.setQ("*:*");
        }
        if (parentQuery.getFacets() == null || parentQuery.getFacets().length == 0) {
            parentQuery.setFacets(new String[]{OccurrenceIndex.NAMES_AND_LSID});
        }

        if (subQuery != null) {
            if (parentQuery.getFacets() != null && parentQuery.getFacets().length == 1) {
                String filename = StringUtils.isNotEmpty(file) ? file : parentQuery.getFacets()[0];
                response.setHeader("Cache-Control", "must-revalidate");
                response.setHeader("Pragma", "must-revalidate");
                response.setHeader("Content-Disposition", "attachment;filename=" + filename + ".csv");
                response.setContentType("text/csv");

                try {
                    searchDao.writeEndemicFacetToStream(subQuery, parentQuery, includeCount, lookupName, includeSynonyms, includeLists, response.getOutputStream());
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                }

            } else {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Please supply only one facet.");
            }
        } else {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Please supply a valid sub query qid.");
        }
    }

    /**
     * @param searchDao the searchDao to set
     */
    public void setSearchDao(SearchDAO searchDao) {
        this.searchDao = searchDao;
    }
}
