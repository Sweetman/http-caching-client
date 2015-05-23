import java.net.*;
import java.io.*;
import java.util.*;

public class CachingHTTPClient {
	//Change this global var to change path of cache file
	// public static String cacheFilePWD = "/tmp/jts2939/assignment1/emptyCache.ser";
	public static String cacheFilePWD = "/tmp/jts2939/assignment1/cache.ser";

	public static void main(String[] args){
		if (args.length < 1) {
			System.out.println("Usage:");
			System.out.println("java CachingHTTPClient <url>");
			System.exit(0);
		}

		HashMap<String, HashMap<String, Object>> map = null;
		URL url = null;

		try {
			url = new URL(args[0]);
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
		assert url != null;

		fileExists();
		map = readCache();
		updateCache(map, url);
	}

	public static void fileExists(){
		try{
			File cacheFile = new File(cacheFilePWD);
			if(!cacheFile.exists()){
				cacheFile.getParentFile().mkdirs();
				cacheFile.createNewFile();
			}
		} catch(IOException e){
			e.printStackTrace();
		}
	}

	public static HashMap<String, HashMap<String, Object>> readCache(){
		HashMap<String, HashMap<String, Object>> map = new HashMap();
		try{
			BufferedReader br = new BufferedReader(new FileReader(cacheFilePWD));     
			if (br.readLine() != null) {
				FileInputStream fis = new FileInputStream(cacheFilePWD);
				ObjectInputStream ois = new ObjectInputStream(fis);
				map = (HashMap<String, HashMap<String, Object>>) ois.readObject();
				ois.close();
			}
		} catch(IOException e){
			e.printStackTrace();
		} catch(ClassNotFoundException e){
			e.printStackTrace();
		}
		return map;
	}

	public static void updateCache(HashMap<String, HashMap<String, Object>> map, URL url){
		try{
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			HttpURLConnection connection2 = (HttpURLConnection) new URL(url.toString()).openConnection();
			if(connection.getResponseCode() == 200 || connection.getResponseCode() == 304){
				if(map.containsKey(connection.getURL().toString())){
					long max_age = 0;
					HashMap<String, Object> data_map = map.get(connection.getURL().toString());
					long date = connection.getDate();
					long expires = (long) data_map.get("Expires");
					long last_accessed_date = (long) data_map.get("Date");
					long last_modified_date = (long) data_map.get("Last Modified");
					connection2.setIfModifiedSince(last_modified_date);
					String s = (String) data_map.get("Cache-Control");
					String[] split_array = s.split("max-age=");
					if(split_array.length > 1){
						max_age = Integer.parseInt(split_array[1])*1000;
					}

					if(max_age > 0){
						if(max_age > (date - last_accessed_date)){
							System.out.println("***** Serving from the cache – start *****");
							printHeadersContent(data_map);
							System.out.println("***** Serving from the cache – end *****");
						}
						else{
							if(connection2.getResponseCode() == 304){
								System.out.println("***** Serving from the cache – start *****");
								printHeadersContent(data_map);
								System.out.println("***** Serving from the cache – end *****");	
							}else if(connection2.getResponseCode() == 200){
								HashMap<String, Object> headers = new HashMap<String, Object>();
								headers = updateHeaders(connection, map);
								System.out.println("***** Serving from the source – start *****");
								printHeadersContent(headers);
								System.out.println("***** Serving from the source – end *****");
							}
						}
					}else if(expires > 0){
						if(date < expires){
							System.out.println("***** Serving from the cache – start *****");
							printHeadersContent(data_map);
							System.out.println("***** Serving from the cache – end *****");
						}else{
							connection2.setIfModifiedSince(last_modified_date);
							if(connection.getResponseCode() == 304){
								System.out.println("***** Serving from the cache – start *****");
								printHeadersContent(data_map);
								System.out.println("***** Serving from the cache – end *****");	
							} else if(connection.getResponseCode() == 200){
								HashMap<String, Object> headers = new HashMap<String, Object>();
								headers = updateHeaders(connection, map);
								System.out.println("***** Serving from the source – start *****");
								printHeadersContent(headers);
								System.out.println("***** Serving from the source – end *****");
							}
						}
					}
				}
				// URL is not in map keys:
				// 1) Establish the connection
				// 2) Check if expires and max_age are present
				// 3) If BOTH are not present, do not store in cache
				//    If EITHER are present, add to cache
				// 4) Print out the response code
				else{
					HashMap<String, Object> headers = new HashMap<String, Object>();
					headers = updateHeaders(connection, map);
					System.out.println("***** Serving from the source – start *****");
					printHeadersContent(headers);
					System.out.println("***** Serving from the source – end *****");
				}
			}
		} catch(IOException e){
			e.printStackTrace();
		}
	}

	public static void printHeadersContent(HashMap<String, Object> headers){
		for(String key : headers.keySet()){
			if(!key.equals("Content"))
				System.out.println(key + ": " + headers.get(key));
			System.out.println("Content: " + headers.get("Content"));
		}
	}

	public static HashMap<String, Object> updateHeaders(HttpURLConnection connection, HashMap<String, HashMap<String, Object>> map){
		HashMap<String, Object> headers = new HashMap<String, Object>();
		try{
			String s = connection.getHeaderField("Cache-Control");
			int max_age = 0;
			if(s != null){
				String[] split_array = s.split("max-age=");
				if(split_array.length > 1){
					max_age = Integer.parseInt(split_array[1])*1000;
				}
			}
			InputStream input = connection.getInputStream();
			BufferedReader reader = new BufferedReader(new InputStreamReader(input));
			StringBuilder content = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				content.append(line);
			}
			reader.close();
			headers.put("Date", connection.getDate());
			headers.put("Expires", connection.getExpiration());
			headers.put("Last Modified", connection.getLastModified());
			headers.put("Cache-Control", connection.getHeaderField("Cache-Control"));
			headers.put("Content-Type", connection.getContentType());
			headers.put("Content-Length", connection.getContentLength());
			headers.put("Content-Encoding", connection.getContentEncoding());
			headers.put("Content", content.toString());

			// if either are present, add to cache
			if(max_age != 0 || connection.getExpiration() != 0){
				map.put(connection.getURL().toString(), headers);
				FileOutputStream fos = new FileOutputStream(cacheFilePWD);
				ObjectOutputStream oos = new ObjectOutputStream(fos);
				oos.writeObject(map);
				oos.close();
			}
		} catch(IOException e){
			e.printStackTrace();
		}

		return headers;
	}

}