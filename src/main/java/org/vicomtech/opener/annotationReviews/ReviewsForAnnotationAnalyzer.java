package org.vicomtech.opener.annotationReviews;

import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.ws.BindingProvider;

import org.apache.commons.io.FileUtils;
import org.vicomtech.opener.bratAdaptionTools.ws.client.OpenerService;
import org.vicomtech.opener.bratAdaptionTools.ws.client.OpenerServiceImplService;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;

public class ReviewsForAnnotationAnalyzer {

	public static final String DIR_WITH_REVIEW_IDS = "reviewsForAnnotations";
	public static final String DIR_WITH_REVIEWS_KAF = DIR_WITH_REVIEW_IDS+"_KAF_2";
//	public static final String[] languages = new String[] { "dutch", "english",
//			"french", "spanish", "german", "italian" };

	public static final String REVIEW_ID_PATTERN_STRING = "\\Q\"review_id\":\"\\E(\\p{Alnum}+)\";\\s\\s1";
	private static final Pattern REVIEW_ID_PATTERN = Pattern
			.compile(REVIEW_ID_PATTERN_STRING);

	private MongoClient mongoClient;
	private DB db;

	private static OpenerService openerService=getOpenerService();
	private Map<String,String>langNameMap;
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		ReviewsForAnnotationAnalyzer reviewsForAnnotationAnalyzer=new ReviewsForAnnotationAnalyzer();
		reviewsForAnnotationAnalyzer.analyzeReviewsForAnnotation();
	}

	public ReviewsForAnnotationAnalyzer() {
		try {
			mongoClient = new MongoClient("localhost", 27017);
			db = mongoClient.getDB("opener-annotation-task");
			langNameMap=Maps.newHashMap();
			langNameMap.put("french", "fr");
			langNameMap.put("spanish", "es");
			langNameMap.put("english", "en");
			langNameMap.put("german", "de");
			langNameMap.put("dutch", "nl");
			langNameMap.put("italian", "it");
		} catch (UnknownHostException e) {
			throw new RuntimeException(e);
		}
	}
	
	public void analyzeReviewsForAnnotation(){
		Map<String, List<String>> reviewIdsPerLanguage = getReviewIdsPerLanguage();
		File dirForKaf=new File(DIR_WITH_REVIEWS_KAF);
		if(!dirForKaf.exists()){
			dirForKaf.mkdirs();
		}
		for(String language:reviewIdsPerLanguage.keySet()){
			System.out.println("Starting with language: "+language);
			File dirForThisLanguage=new File(dirForKaf.getAbsolutePath()+File.separator+language);
			if(!dirForThisLanguage.exists()){
				dirForThisLanguage.mkdirs();
			}
			System.out.println("Directory for this language: "+dirForThisLanguage.getAbsolutePath());
			List<String> reviewIdsForThisLanguage=reviewIdsPerLanguage.get(language);
			int count=0;
			for(String reviewId:reviewIdsForThisLanguage){
				System.out.println("Analyzing review with id: "+reviewId+" (count: "+(++count)+")");
				ReviewInfo reviewInfo = getReviewInfo(reviewId);
				String title=reviewInfo.getTitle()!=null?reviewInfo.getTitle():"";
				String comment=reviewInfo.getComment()!=null?reviewInfo.getComment():"";
				String analyzableContent=title+"\n"+comment;
				analyzableContent=analyzableContent.trim();
				String kaf=analyzeReviewWithOpeNER(analyzableContent, langNameMap.get(language));
				String kafFileName=language+"_"+reviewId+".kaf";
				File kafFile=new File(dirForThisLanguage.getAbsolutePath()+File.separator+kafFileName);
				System.out.println("Going to write the kaf file to: "+kafFile.getAbsolutePath());
				try {
					FileUtils.write(kafFile, kaf, "UTF-8");
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		}
	}
	

	public Map<String, List<String>> getReviewIdsPerLanguage() {
		Map<String, List<String>> reviewIdsPerLanguage = Maps.newHashMap();
		File file = new File(DIR_WITH_REVIEW_IDS);
		File[] files = file.listFiles();
		for (File f : files) {
			String fname = f.getName();
			if (fname.endsWith("_id.txt")) {
				String currentLanguage = fname.split("_")[0];
				List<String> reviewIds = readIdsFromFile(f);
				reviewIdsPerLanguage.put(currentLanguage, reviewIds);
			}
		}
		return reviewIdsPerLanguage;
	}

	public List<String> readIdsFromFile(File file) {
		try {
			List<String> reviewIds = Lists.newArrayList();
			List<String> lines = FileUtils.readLines(file);
			Matcher matcher;
			for (String line : lines) {
				matcher = REVIEW_ID_PATTERN.matcher(line);
				if (matcher.find()) {
					reviewIds.add(matcher.group(1));
				}
			}
			return reviewIds;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public ReviewInfo getReviewInfo(String reviewId) {
		DBCollection collection = db.getCollection("reviews-all");
		BasicDBObject query = new BasicDBObject("review_id", reviewId);
		DBObject dbObject = collection.findOne(query);
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		ReviewInfo reviewInfo = gson.fromJson(dbObject.toString(),
				ReviewInfo.class);
		return reviewInfo;
	}
	
	public String analyzeReviewWithOpeNER(String reviewContent, String expectedLang){
		String kaf=openerService.tokenize(reviewContent, expectedLang);
		kaf=openerService.postag(kaf, expectedLang);
		return kaf;
	}
	
	protected static OpenerService getOpenerService(){
		OpenerServiceImplService serviceImpl = new OpenerServiceImplService();
		OpenerService service = serviceImpl.getOpenerServiceImplPort();
		// La URL que quieras, esto es lo que deberías obtener mediante
		// configuración externa
		String endpointURL = "http://192.168.17.128:9999/ws/opener?wsdl";
		BindingProvider bp = (BindingProvider) service;
		bp.getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY,
				endpointURL);
		return service;
	}

	/**
	 * Internal encapsulation class for review basic inf (id+title+content)
	 * @author agarciap
	 *
	 */
	public static class ReviewInfo {
		private String review_id;
		private String title;
		private String comment;

		public ReviewInfo() {
			super();
		}

		public String getReview_id() {
			return review_id;
		}

		public void setReview_id(String review_id) {
			this.review_id = review_id;
		}

		public String getTitle() {
			return title;
		}

		public void setTitle(String title) {
			this.title = title;
		}

		public String getComment() {
			return comment;
		}

		public void setComment(String comment) {
			this.comment = comment;
		}
	}
}
