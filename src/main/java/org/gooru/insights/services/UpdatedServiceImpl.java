package org.gooru.insights.services;

import java.sql.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.index.query.AndFilterBuilder;
import org.elasticsearch.index.query.BoolFilterBuilder;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.MatchAllFilterBuilder;
import org.elasticsearch.index.query.NestedFilterBuilder;
import org.elasticsearch.index.query.NestedFilterParser;
import org.elasticsearch.index.query.NotFilterBuilder;
import org.elasticsearch.index.query.OrFilterBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.filter.FilterAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogram;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregator;
import org.elasticsearch.search.aggregations.bucket.terms.TermsBuilder;
import org.gooru.insights.models.RequestParamsDTO;
import org.gooru.insights.models.RequestParamsFilterDetailDTO;
import org.gooru.insights.models.RequestParamsFilterFieldsDTO;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;

@Service
public class UpdatedServiceImpl implements UpdatedService{

	@Autowired
	BaseConnectionService baseConnectionService;
	
	@Autowired
	BaseAPIService baseAPIService;
	
	
	public boolean aggregate(RequestParamsDTO requestParamsDTO,SearchRequestBuilder searchRequestBuilder,Map<String,String> metricsName) {
		try{
			TermsBuilder termBuilder = null;
			String[] groupBy = requestParamsDTO.getGroupBy().split(",");
			for(int i=groupBy.length-1; i >= 0;i--){
				TermsBuilder tempBuilder = null;
				if(termBuilder != null){
						tempBuilder = AggregationBuilders.terms("field"+i).field(esFields(groupBy[i]));
						tempBuilder.subAggregation(termBuilder);
						termBuilder = tempBuilder;
				}else{
					termBuilder = AggregationBuilders.terms("field"+i).field(esFields(groupBy[i]));
				}
				termBuilder.size(1000);
				System.out.println("i"+i+"groupBy -1 :"+(groupBy.length-1));
				if( i == groupBy.length-1){
					System.out.println("expected");
					includeAggregation(requestParamsDTO, termBuilder,metricsName);
				}
			}
			if(baseAPIService.checkNull(requestParamsDTO.getFilter())){
				FilterAggregationBuilder filterBuilder = null;
			if(filterBuilder == null){
				filterBuilder = includeFilters(requestParamsDTO.getFilter());
//				filterBuilder = addFilters(requestParamsDTO.getFilter());
			}
			searchRequestBuilder.addAggregation(filterBuilder);
			}else{
				searchRequestBuilder.addAggregation(termBuilder);
			}
			return true;
	}catch(Exception e){
		e.printStackTrace();
		return false;
	}
	}
	
	public boolean granularityAggregate(RequestParamsDTO requestParamsDTO,SearchRequestBuilder searchRequestBuilder,Map<String,String> metricsName) {
		try{
			TermsBuilder termBuilder = null;
			DateHistogramBuilder dateHistogram = null;
			String[] groupBy = requestParamsDTO.getGroupBy().split(",");
			boolean isFirstDateHistogram = false;
			for(int i=groupBy.length-1; i >= 0;i--){
				TermsBuilder tempBuilder = null;
				String groupByName = esFields(groupBy[i]);
				//date field checker	
				if(baseConnectionService.getFieldsDataType().containsKey(groupBy[i]) && baseConnectionService.getFieldsDataType().get(groupBy[i]).equalsIgnoreCase("date")){
					System.out.println("entered date histogram");
					dateHistogram = dateHistogram(requestParamsDTO.getGranularity(),"field"+i,groupByName);
					isFirstDateHistogram =true;
					if(termBuilder != null){
						dateHistogram.subAggregation(termBuilder);
						dateHistogram.minDocCount(1000);
						termBuilder = null;
						}
					}else{
						
						if(termBuilder != null){
						tempBuilder = AggregationBuilders.terms("field"+i).field(groupByName);
						if(dateHistogram != null){
							if(termBuilder != null){
								dateHistogram.subAggregation(termBuilder);
							}
							
						}else{
						tempBuilder.subAggregation(termBuilder);
						}
						termBuilder = tempBuilder;
						}else{
							termBuilder = AggregationBuilders.terms("field"+i).field(groupByName);
						}
						if(dateHistogram != null){
							termBuilder.subAggregation(dateHistogram);
							dateHistogram = null;
						}
						termBuilder.size(1000);
						isFirstDateHistogram =false;
					}
				System.out.println("i"+i+"groupBy -1 :"+(groupBy.length-1));
				if( i == groupBy.length-1 && !isFirstDateHistogram){
					System.out.println("expected");
					if(termBuilder != null ){
					includeAggregation(requestParamsDTO, termBuilder,metricsName);
					}
					}
				
				if( i == groupBy.length-1 && isFirstDateHistogram){
					System.out.println("expected");
					if(dateHistogram != null ){
					includeAggregation(requestParamsDTO, dateHistogram,metricsName);
					}
					}
			}
			if(baseAPIService.checkNull(requestParamsDTO.getFilter())){
				FilterAggregationBuilder filterBuilder = null;
			if(filterBuilder == null){
				filterBuilder = includeFilters(requestParamsDTO.getFilter());
//				filterBuilder = addFilters(requestParamsDTO.getFilter());
			}
			if(isFirstDateHistogram){
				filterBuilder.subAggregation(dateHistogram);
			}else{
				filterBuilder.subAggregation(termBuilder);	
			}
			searchRequestBuilder.addAggregation(filterBuilder);
			}else{
				searchRequestBuilder.addAggregation(termBuilder);
			}
			return true;
	}catch(Exception e){
		e.printStackTrace();
		return false;
	}
	}
	
	public void includeAggregation(RequestParamsDTO requestParamsDTO,TermsBuilder termBuilder,Map<String,String> metricsName){
	if (!requestParamsDTO.getAggregations().isEmpty()) {
		try{
		Gson gson = new Gson();
		String requestJsonArray = gson
				.toJson(requestParamsDTO.getAggregations());
		JSONArray jsonArray = new JSONArray(
				requestJsonArray);
		for (int i = 0; i < jsonArray.length(); i++) {
			JSONObject jsonObject;
			jsonObject = new JSONObject(jsonArray.get(i)
					.toString());
			if (!jsonObject.has("formula")
					&& !jsonObject.has("requestValues")) {
				continue;
			}
				if (baseAPIService.checkNull(jsonObject
						.get("formula"))) {
						String requestValues = jsonObject
								.get("requestValues")
								.toString();
						String metricField[] =requestValues.split(","); 
						for (int j=0;j<metricField.length;j++) {
							if (!jsonObject
									.has(metricField[j])) {
								continue;
							}
							String fieldName = esFields(jsonObject.get(metricField[j]).toString());
						performAggregation(termBuilder,jsonObject,jsonObject.getString("formula"), "metrics"+i,fieldName);
						metricsName.put(jsonObject.getString("name") != null ? jsonObject.getString("name") : fieldName, "metrics"+i);

						}
				}
		}
	}catch(Exception e){
		e.printStackTrace();
	}
	}
	}
	
	public void includeAggregation(RequestParamsDTO requestParamsDTO,DateHistogramBuilder  dateHistogramBuilder,Map<String,String> metricsName){
		if (!requestParamsDTO.getAggregations().isEmpty()) {
			try{
			Gson gson = new Gson();
			String requestJsonArray = gson
					.toJson(requestParamsDTO.getAggregations());
			JSONArray jsonArray = new JSONArray(
					requestJsonArray);
			for (int i = 0; i < jsonArray.length(); i++) {
				JSONObject jsonObject;
				jsonObject = new JSONObject(jsonArray.get(i)
						.toString());
				if (!jsonObject.has("formula")
						&& !jsonObject.has("requestValues")) {
					continue;
				}
					if (baseAPIService.checkNull(jsonObject
							.get("formula"))) {
							String requestValues = jsonObject
									.get("requestValues")
									.toString();
							String aggregateName[] = requestValues
									.split(",");
							for (int j=0;j<aggregateName.length;j++) {
								if (!jsonObject
										.has(aggregateName[j])) {
									continue;
								}
								String fieldName = esFields(jsonObject.get(aggregateName[j]).toString());
							performAggregation(dateHistogramBuilder,jsonObject,jsonObject.getString("formula"), "metrics"+j,fieldName);
							metricsName.put(jsonObject.getString("name") != null ? jsonObject.getString("name") : fieldName, fieldName);

							}
					}
			}
		}catch(Exception e){
			e.printStackTrace();
		}
		}
		}
	
	public void performAggregation(TermsBuilder mainFilter,JSONObject jsonObject,String aggregateType,String aggregateName,String fieldName){
		try {
			String esAggregateName= esFields(jsonObject.get(fieldName).toString());
			if("SUM".equalsIgnoreCase(aggregateType)){
			mainFilter
			.subAggregation(AggregationBuilders
					.sum(aggregateName)
					.field(aggregateName));
			}else if("AVG".equalsIgnoreCase(aggregateType)){
				mainFilter
				.subAggregation(AggregationBuilders.avg(aggregateName).field(esAggregateName));
			}else if("MAX".equalsIgnoreCase(aggregateType)){
				mainFilter
				.subAggregation(AggregationBuilders.max(aggregateName).field(esAggregateName));
			}else if("MIN".equalsIgnoreCase(aggregateType)){
				mainFilter
				.subAggregation(AggregationBuilders.min(aggregateName).field(esAggregateName));
				
			}else if("COUNT".equalsIgnoreCase(aggregateType)){
				mainFilter
				.subAggregation(AggregationBuilders.count(aggregateName).field(esAggregateName));
			}else if("DISTINCT".equalsIgnoreCase(aggregateType)){
				mainFilter
				.subAggregation(AggregationBuilders.cardinality(aggregateName).field(esAggregateName));
			}
	
		} catch (Exception e) {
			e.printStackTrace();
		} 
		}
	
	public void performAggregation(DateHistogramBuilder dateHistogramBuilder,JSONObject jsonObject,String aggregateType,String aggregateName,String fieldName){
		try {
			String esAggregateName= esFields(jsonObject.get(fieldName).toString());
			if("SUM".equalsIgnoreCase(aggregateType)){
				dateHistogramBuilder
			.subAggregation(AggregationBuilders
					.sum(esAggregateName)
					.field(esAggregateName));
			}else if("AVG".equalsIgnoreCase(aggregateType)){
				dateHistogramBuilder
				.subAggregation(AggregationBuilders.avg(aggregateName).field(esAggregateName));
			}else if("MAX".equalsIgnoreCase(aggregateType)){
				dateHistogramBuilder
				.subAggregation(AggregationBuilders.max(aggregateName).field(esAggregateName));
			}else if("MIN".equalsIgnoreCase(aggregateType)){
				dateHistogramBuilder
				.subAggregation(AggregationBuilders.min(aggregateName).field(esAggregateName));
				
			}else if("COUNT".equalsIgnoreCase(aggregateType)){
				dateHistogramBuilder
				.subAggregation(AggregationBuilders.count(aggregateName).field(esAggregateName));
			}else if("DISTINCT".equalsIgnoreCase(aggregateType)){
				dateHistogramBuilder
				.subAggregation(AggregationBuilders.cardinality(aggregateName).field(esAggregateName));
			}
	
		} catch (JSONException e) {
			e.printStackTrace();
		} 
		}
	
	//search Filter
		public FilterAggregationBuilder addFilters(
				List<RequestParamsFilterDetailDTO> requestParamsFiltersDetailDTO) {
			MatchAllFilterBuilder subFilter = FilterBuilders.matchAllFilter();
			FilterAggregationBuilder filterBuilder = new FilterAggregationBuilder("filters");
			if (requestParamsFiltersDetailDTO != null) {
				for (RequestParamsFilterDetailDTO fieldData : requestParamsFiltersDetailDTO) {
				
					if (fieldData != null) {
						List<RequestParamsFilterFieldsDTO> requestParamsFilterFieldsDTOs = fieldData
								.getFields();
						BoolFilterBuilder boolFilter = FilterBuilders.boolFilter();
						for (RequestParamsFilterFieldsDTO fieldsDetails : requestParamsFilterFieldsDTOs) {
							FilterBuilder filter = null;
							String fieldName = esFields(fieldsDetails.getFieldName());
							if (fieldsDetails.getType()
									.equalsIgnoreCase("selector")) {
								if (fieldsDetails.getOperator().equalsIgnoreCase(
										"rg")) {
									boolFilter.must(FilterBuilders
											.rangeFilter(fieldName)
											.from(checkDataType(
													fieldsDetails.getFrom(),
													fieldsDetails.getValueType(),fieldsDetails.getFormat()))
											.to(checkDataType(
													fieldsDetails.getTo(),
													fieldsDetails.getValueType(),fieldsDetails.getFormat())));
								} else if (fieldsDetails.getOperator()
										.equalsIgnoreCase("nrg")) {
									boolFilter.must(FilterBuilders
											.rangeFilter(fieldName)
											.from(checkDataType(
													fieldsDetails.getFrom(),
													fieldsDetails.getValueType(),fieldsDetails.getFormat()))
											.to(checkDataType(
													fieldsDetails.getTo(),
													fieldsDetails.getValueType(),fieldsDetails.getFormat())));
								} else if (fieldsDetails.getOperator()
										.equalsIgnoreCase("eq")) {
									boolFilter.must(FilterBuilders.termFilter(
											fieldName,
											checkDataType(fieldsDetails.getValue(),
													fieldsDetails.getValueType(),fieldsDetails.getFormat())));
								} else if (fieldsDetails.getOperator()
										.equalsIgnoreCase("lk")) {
									boolFilter.must(FilterBuilders.prefixFilter(
											fieldName,
											checkDataType(fieldsDetails.getValue(),
													fieldsDetails.getValueType(),fieldsDetails.getFormat())
													.toString()));
								} else if (fieldsDetails.getOperator()
										.equalsIgnoreCase("ex")) {
									boolFilter.must(FilterBuilders
											.existsFilter(checkDataType(
													fieldsDetails.getValue(),
													fieldsDetails.getValueType(),fieldsDetails.getFormat())
													.toString()));
								}   else if (fieldsDetails.getOperator()
										.equalsIgnoreCase("in")) {
									boolFilter.must(FilterBuilders.inFilter(fieldName,
											fieldsDetails.getValue().split(",")));
								} else if (fieldsDetails.getOperator()
										.equalsIgnoreCase("le")) {
									boolFilter.must(FilterBuilders.rangeFilter(
											fieldName).lte(
											checkDataType(fieldsDetails.getValue(),
													fieldsDetails.getValueType(),fieldsDetails.getFormat())));
								} else if (fieldsDetails.getOperator()
										.equalsIgnoreCase("ge")) {
									boolFilter.must(FilterBuilders.rangeFilter(
											fieldName).gte(
											checkDataType(fieldsDetails.getValue(),
													fieldsDetails.getValueType(),fieldsDetails.getFormat())));
								} else if (fieldsDetails.getOperator()
										.equalsIgnoreCase("lt")) {
									boolFilter.must(FilterBuilders.rangeFilter(
											fieldName).lt(
											checkDataType(fieldsDetails.getValue(),
													fieldsDetails.getValueType(),fieldsDetails.getFormat())));
								} else if (fieldsDetails.getOperator()
										.equalsIgnoreCase("gt")) {
									boolFilter.must(FilterBuilders.rangeFilter(
											fieldName).gt(
											checkDataType(fieldsDetails.getValue(),
													fieldsDetails.getValueType(),fieldsDetails.getFormat())));
								}
					}
						}
							if (fieldData.getLogicalOperatorPrefix().equalsIgnoreCase(
									"AND")) {
								filterBuilder.filter(FilterBuilders.andFilter(boolFilter));
//								subFilter.must(FilterBuilders.andFilter(boolFilter));
//								filterBuilder.filter(FilterBuilders.andFilter(boolFilter));
							} else if (fieldData.getLogicalOperatorPrefix()
									.equalsIgnoreCase("OR")) {
								filterBuilder.filter(FilterBuilders.orFilter(boolFilter));
//								filterBuilder.filter(FilterBuilders.orFilter(boolFilter));
							} else if (fieldData.getLogicalOperatorPrefix()
									.equalsIgnoreCase("NOT")) {
								filterBuilder.filter(FilterBuilders.notFilter(boolFilter));
//								filterBuilder.filter(FilterBuilders.notFilter(boolFilter));
							
						}
						
					}
				}
				filterBuilder.filter(subFilter);
			}
			
			return filterBuilder;
		}

		public FilterAggregationBuilder includeFilters(
				List<RequestParamsFilterDetailDTO> requestParamsFiltersDetailDTO) {
			FilterAggregationBuilder filterBuilder = new FilterAggregationBuilder("filters");
			if (requestParamsFiltersDetailDTO != null) {
				BoolFilterBuilder boolFilter =FilterBuilders.boolFilter();
				for (RequestParamsFilterDetailDTO fieldData : requestParamsFiltersDetailDTO) {
					if (fieldData != null) {
						List<RequestParamsFilterFieldsDTO> requestParamsFilterFieldsDTOs = fieldData
								.getFields();
			for (RequestParamsFilterFieldsDTO fieldsDetails : requestParamsFilterFieldsDTOs) {
				FilterBuilder filter = null;
				String fieldName = esFields(fieldsDetails.getFieldName());
				if (fieldsDetails.getType()
						.equalsIgnoreCase("selector")) {
					if (fieldsDetails.getOperator().equalsIgnoreCase(
							"rg")) {
							filter = FilterBuilders
								.rangeFilter(fieldName)
								.from(checkDataType(
										fieldsDetails.getFrom(),
										fieldsDetails.getValueType(),fieldsDetails.getFormat()))
								.to(checkDataType(
										fieldsDetails.getTo(),
										fieldsDetails.getValueType(),fieldsDetails.getFormat()));
					} else if (fieldsDetails.getOperator()
							.equalsIgnoreCase("nrg")) {
						filter =  FilterBuilders
								.rangeFilter(fieldName)
								.from(checkDataType(
										fieldsDetails.getFrom(),
										fieldsDetails.getValueType(),fieldsDetails.getFormat()))
								.to(checkDataType(
										fieldsDetails.getTo(),
										fieldsDetails.getValueType(),fieldsDetails.getFormat()));
					} else if (fieldsDetails.getOperator()
							.equalsIgnoreCase("eq")) {
						filter = FilterBuilders.termFilter(
								fieldName,
								checkDataType(fieldsDetails.getValue(),
										fieldsDetails.getValueType(),fieldsDetails.getFormat()));
					} else if (fieldsDetails.getOperator()
							.equalsIgnoreCase("lk")) {
						filter =  FilterBuilders.prefixFilter(
								fieldName,
								checkDataType(fieldsDetails.getValue(),
										fieldsDetails.getValueType(),fieldsDetails.getFormat())
										.toString());
					} else if (fieldsDetails.getOperator()
							.equalsIgnoreCase("ex")) {
						filter = FilterBuilders
								.existsFilter(checkDataType(
										fieldsDetails.getValue(),
										fieldsDetails.getValueType(),fieldsDetails.getFormat())
										.toString());
					}   else if (fieldsDetails.getOperator()
							.equalsIgnoreCase("in")) {
						filter = FilterBuilders.inFilter(fieldName,
								fieldsDetails.getValue().split(","));
					} else if (fieldsDetails.getOperator()
							.equalsIgnoreCase("le")) {
						filter = FilterBuilders.rangeFilter(
								fieldName).lte(
								checkDataType(fieldsDetails.getValue(),
										fieldsDetails.getValueType(),fieldsDetails.getFormat()));
					} else if (fieldsDetails.getOperator()
							.equalsIgnoreCase("ge")) {
						filter = FilterBuilders.rangeFilter(
								fieldName).gte(
								checkDataType(fieldsDetails.getValue(),
										fieldsDetails.getValueType(),fieldsDetails.getFormat()));
					} else if (fieldsDetails.getOperator()
							.equalsIgnoreCase("lt")) {
						filter = FilterBuilders.rangeFilter(
								fieldName).lt(
								checkDataType(fieldsDetails.getValue(),
										fieldsDetails.getValueType(),fieldsDetails.getFormat()));
					} else if (fieldsDetails.getOperator()
							.equalsIgnoreCase("gt")) {
						filter = FilterBuilders.rangeFilter(
								fieldName).gt(
								checkDataType(fieldsDetails.getValue(),
										fieldsDetails.getValueType(),fieldsDetails.getFormat()));
					}
					}

			
			if (fieldData.getLogicalOperatorPrefix().equalsIgnoreCase(
					"AND")) {
					boolFilter.must(filter);
			}else if (fieldData.getLogicalOperatorPrefix().equalsIgnoreCase(
					"OR")) {
					boolFilter.should(filter);
			}else if (fieldData.getLogicalOperatorPrefix().equalsIgnoreCase(
					"NOT")) {
					boolFilter.mustNot(filter);
			}
			}
					}
				}
				filterBuilder.filter(boolFilter);
			}
			return filterBuilder;
		}
		
		public void includeFilter(BoolFilterBuilder boolFilter,List<RequestParamsFilterFieldsDTO> requestParamsFilterFieldsDTOs){
			
		}
				
		public Object checkDataType(String value, String valueType,String dateformat) {
			
			SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd kk:mm:ss");
			
			if(baseAPIService.checkNull(dateformat)){
				try{
				format = new SimpleDateFormat(dateformat);
				}catch(Exception e){
					
				}
			}
			if (valueType.equalsIgnoreCase("String")) {
				return value;
			} else if (valueType.equalsIgnoreCase("Long")) {
				return Long.valueOf(value);
			} else if (valueType.equalsIgnoreCase("Integer")) {
				return Integer.valueOf(value);
			} else if (valueType.equalsIgnoreCase("Double")) {
				return Double.valueOf(value);
			} else if (valueType.equalsIgnoreCase("Short")) {
				return Short.valueOf(value);
			}else if (valueType.equalsIgnoreCase("Date")) {
				try {
					return format.parse(value).getTime();
				} catch (ParseException e) {
					e.printStackTrace();
					return value.toString();
				}
			}
			return Integer.valueOf(value);
		}
		
		public String esFields(String fields){
			Map<String,String> mappingfields = baseConnectionService.getFields();
			StringBuffer esFields = new StringBuffer();
			for(String field : fields.split(",")){
				if(esFields.length() > 0){
					esFields.append(",");
				}
				if(mappingfields.containsKey(field)){
					esFields.append(mappingfields.get(field));
				}else{
					esFields.append(field);
				}
			}
			return esFields.toString();
		}
		public DateHistogramBuilder dateHistogram(String granularity,String fieldName,String field){
			
			String format ="yyyy-MM-dd hh:kk:ss";
			if(baseAPIService.checkNull(granularity)){
				org.elasticsearch.search.aggregations.bucket.histogram.DateHistogram.Interval interval = DateHistogram.Interval.DAY;
				if(granularity.equalsIgnoreCase("year")){
					interval = DateHistogram.Interval.YEAR;
					format ="yyyy";
				}else if(granularity.equalsIgnoreCase("day")){
					interval = DateHistogram.Interval.DAY;
					format ="yyyy-MM-dd";
				}else if(granularity.equalsIgnoreCase("month")){
					interval = DateHistogram.Interval.MONTH;
					format ="yyyy-MM";
				}else if(granularity.equalsIgnoreCase("hour")){
					interval = DateHistogram.Interval.HOUR;
					format ="yyyy-MM-dd hh";
				}else if(granularity.equalsIgnoreCase("minute")){
					interval = DateHistogram.Interval.MINUTE;
					format ="yyyy-MM-dd hh:kk";
				}else if(granularity.equalsIgnoreCase("second")){
					interval = DateHistogram.Interval.SECOND;
				}else if(granularity.equalsIgnoreCase("quarter")){
					interval = DateHistogram.Interval.QUARTER;
					format ="yyyy-MM-dd";
				}else if(granularity.equalsIgnoreCase("week")){
					format ="yyyy-MM-dd";
					interval = DateHistogram.Interval.WEEK;
				}else if(granularity.endsWith("d")){
					int days = new Integer(granularity.replace("d",""));
					format ="yyyy-MM-dd";
					interval = DateHistogram.Interval.days(days);
				}else if(granularity.endsWith("w")){
					int weeks = new Integer(granularity.replace("w",""));
					format ="yyyy-MM-dd";
					interval = DateHistogram.Interval.weeks(weeks);
				}else if(granularity.endsWith("h")){
					int hours = new Integer(granularity.replace("h",""));
					format ="yyyy-MM-dd hh";
					interval = DateHistogram.Interval.hours(hours);
				}else if(granularity.endsWith("k")){
					int minutes = new Integer(granularity.replace("k",""));
					format ="yyyy-MM-dd hh:kk";
					interval = DateHistogram.Interval.minutes(minutes);
				}else if(granularity.endsWith("s")){
					int seconds = new Integer(granularity.replace("s",""));
					interval = DateHistogram.Interval.seconds(seconds);
				}
				
				DateHistogramBuilder dateHistogram = AggregationBuilders.dateHistogram(fieldName).field(field).interval(interval).format(format);
				return dateHistogram;
		}
			return null;
	}
		
		public List<Map<String,Object>> buildAggregateJSON(String groupBy,String resultData,Map<String,String> metrics,boolean hasFilter){

			List<Map<String,Object>> dataMap = new ArrayList<Map<String,Object>>();
			try {
				int counter=0;
				String[] fields = groupBy.split(",");
				JSONObject json = new JSONObject(resultData);
				json = new JSONObject(json.get("aggregations").toString());
				if(hasFilter){
					json = new JSONObject(json.get("filters").toString());
				}
				Map<Object,Map<String,Object>> intermediateMap = new HashMap<Object,Map<String,Object>>(); 
				List<Map<Object,Map<String,Object>>> intermediateList = new ArrayList<Map<Object,Map<String, Object>>>(); 
				while(counter < fields.length){
					System.out.println("json "+json);
					if(json.length() > 0){
					JSONObject requestJSON = new JSONObject(json.get("field"+counter).toString());
				JSONArray jsonArray = new JSONArray(requestJSON.get("buckets").toString());
				JSONArray subJsonArray = new JSONArray();
				boolean hasSubAggregate = false;
				boolean hasRecord = false;
				for(int i=0;i<jsonArray.length();i++){
					hasRecord = true;
					JSONObject newJson = new JSONObject(jsonArray.get(i).toString());
					Object key=newJson.get("key");
//					if(counter == (fields.length -1)){
						if(counter+1 == (fields.length)){
						Map<String,Object> resultMap = new LinkedHashMap<String,Object>();
						for(Map.Entry<String,String> entry : metrics.entrySet()){
							if(newJson.has(entry.getValue())){
								resultMap.put(entry.getKey(), new JSONObject(newJson.get(entry.getValue()).toString()).get("value"));
								resultMap.put(fields[counter], newJson.get("key"));
							}
							}
						if(baseAPIService.checkNull(intermediateMap.get(key))){
						resultMap.putAll(intermediateMap.get(key));
						}
						dataMap.add(resultMap);
					}else{
						JSONArray tempArray = new JSONArray();
						newJson = new JSONObject(newJson.get("field"+(counter+1)).toString());
						tempArray = new JSONArray(newJson.get("buckets").toString());
						String data ="";
						for(int j=0;j<tempArray.length();j++){
							JSONObject subJson = new JSONObject(tempArray.get(j).toString());
								Map<String,Object> tempMap = new HashMap<String, Object>();
								if(intermediateMap.containsKey(key)){
									tempMap.putAll(intermediateMap.get(key));
									tempMap.put(fields[counter], key);
									intermediateMap.put(subJson.get("key"),tempMap);
								}else{
									tempMap.put(fields[counter], key);
									intermediateMap.put(subJson.get("key"), tempMap);
								}
							subJsonArray.put(tempArray.get(j));
						}
						hasSubAggregate = true;
					}
				}
				if(hasSubAggregate){
					json = new JSONObject();
					requestJSON.put("buckets", subJsonArray);
					json.put("field"+(counter+1), requestJSON);
				}
				
				if(!hasRecord){
					json = new JSONObject();	
				}
					}
				counter++;
				}
			} catch (JSONException e) {
				System.out.println("some logical problem in filter aggregate json ");
				e.printStackTrace();
			}
			System.out.println("dataMap "+dataMap);
			return dataMap;
		}
		public Map<Integer,Map<String,Object>> processAggregateJSON(String groupBy,String resultData,Map<String,String> metrics,boolean hasFilter){

			Map<Integer,Map<String,Object>> dataMap = new LinkedHashMap<Integer,Map<String,Object>>();
			try {
				int counter=0;
				String[] fields = groupBy.split(",");
				JSONObject json = new JSONObject(resultData);
				json = new JSONObject(json.get("aggregations").toString());
				if(hasFilter){
					json = new JSONObject(json.get("filters").toString());
				}
				while(counter < fields.length){
					JSONObject requestJSON = new JSONObject(json.get("field"+counter).toString());
				JSONArray jsonArray = new JSONArray(requestJSON.get("buckets").toString());
				JSONArray subJsonArray = new JSONArray();
				boolean hasSubAggregate = false;
				for(int i=0;i<jsonArray.length();i++){
					JSONObject newJson = new JSONObject(jsonArray.get(i).toString());
					Object key=newJson.get("key");
//					if(counter == (fields.length -1)){
						if(counter+1 == (fields.length)){
						Map<String,Object> resultMap = new LinkedHashMap<String,Object>();
						boolean processed = false;
						for(Map.Entry<String,String> entry : metrics.entrySet()){
							if(newJson.has(entry.getValue())){
								resultMap.put(entry.getKey(), new JSONObject(newJson.get(entry.getValue()).toString()).get("value"));
								resultMap.put(fields[counter], newJson.get("key"));
							}
							}
						if(baseAPIService.checkNull(dataMap)){
							if(dataMap.containsKey(i)){
								processed = true;
								Map<String,Object> tempMap = new LinkedHashMap<String,Object>();
								tempMap = dataMap.get(i);
								resultMap.putAll(tempMap);
								dataMap.put(i, resultMap);
							}
						}
						if(!processed){
							dataMap.put(i, resultMap);	
						}
							
					}else{
						JSONArray tempArray = new JSONArray();
						newJson = new JSONObject(newJson.get("field"+(counter+1)).toString());
						tempArray = new JSONArray(newJson.get("buckets").toString());
						for(int j=0;j<tempArray.length();j++){
							subJsonArray.put(tempArray.get(j));
						}
						Map<String,Object> tempMap = new LinkedHashMap<String,Object>();
						if(baseAPIService.checkNull(dataMap)){
							if(dataMap.containsKey(i)){
								tempMap = dataMap.get(i);
							}
						}
						tempMap.put(fields[counter], key);
						System.out.println("tempMap "+tempMap);
						dataMap.put(i, tempMap);
						hasSubAggregate = true;
					}
				}
				if(hasSubAggregate){
					json = new JSONObject();
					requestJSON.put("buckets", subJsonArray);
					json.put("field"+(counter+1), requestJSON);
				}
				
				counter++;
				}
			} catch (JSONException e) {
				System.out.println("some logical problem in filter aggregate json ");
				e.printStackTrace();
			}
			System.out.println("dataMap "+dataMap);
			return dataMap;
		}
}

 