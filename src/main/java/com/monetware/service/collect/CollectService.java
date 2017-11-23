package com.monetware.service.collect;


import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.NicelyResynchronizingAjaxController;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.*;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.jsoup.helper.StringUtil.isNumeric;

/**
 * @author xuantang
 */
@Component
public class CollectService {
	/**
	 * the type of rule
	 */
	public static int TYPE_CLUES_AJAX_FLIP = 0;
	public static int TYPE_CLUES = 1;
	public static int TYPE_CLUES_AJAX_CLICK = 2;
	/**
	 *
	 */
	private OnCrawleLinstener onCrawleLinstener;


	class CollectProcessor {
		private int collectType = -1;
		private OnCrawleLinstener onCrawleLinstener;
		private String ajaxXpath;
		private List<String> xpaths;
		private String url;


		public List<String> getXpaths() {
			return xpaths;
		}

		public void setXpaths(List<String> xpaths) {
			this.xpaths = xpaths;
		}

		public String getAjaxXpath() {
			return ajaxXpath;
		}

		public void setAjaxXpath(String ajaxXpath) {
			this.ajaxXpath = ajaxXpath;
		}
		public OnCrawleLinstener getOnCrawleLinstener() {
			return onCrawleLinstener;
		}

		public void setOnCrawleLinstener(OnCrawleLinstener onCrawleLinstener) {
			this.onCrawleLinstener = onCrawleLinstener;
		}

		CollectProcessor(String url, int collectType) {
			this.collectType = collectType;
			this.url = url;
		}

		/**
		 * process the page, extract urls to fetch, extract the data and store
		 * use htmlunit ad a spider
		 * @param page page
		 */
		public void start() {
			// new webclient and initialize configure
			WebClient webClient = new WebClient(BrowserVersion.CHROME);
			webClient.getOptions().setJavaScriptEnabled(true);
			webClient.getOptions().setCssEnabled(false);
			webClient.setAjaxController(new NicelyResynchronizingAjaxController());
			webClient.getOptions().setTimeout(35000);
			webClient.getOptions().setThrowExceptionOnScriptError(false);
			webClient.addRequestHeader("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/62.0.3192.0 Safari/537.36");
			try {
				//　get page
				HtmlPage page = webClient.getPage(url);
				webClient.waitForBackgroundJavaScript(10000);

				// ajax flip and clues
				if(collectType == CollectService.TYPE_CLUES_AJAX_FLIP) {
					// note the page
					List<Integer> pages = new ArrayList<>();
					Integer currentPage = 1;
					boolean isEnd = true;
					pages.add(1);
					List<String> results = new ArrayList<>();
					while(true){
						// get absolute xpath
						List<String> mXpaths = produceXpaths(page, xpaths);
						// extract content
						results.addAll(getContent(page, mXpaths));
						// next page
						List<HtmlAnchor> htmlListItems = page.getByXPath(ajaxXpath);
						for(HtmlAnchor htmlAnchor: htmlListItems){
							String number = htmlAnchor.asText();
							if(isNumeric(number)){
								Integer pageNumber = Integer.valueOf(number);
								if(pageNumber > currentPage){
									isEnd = false;
									pages.add(pageNumber);
									currentPage = pageNumber;
									htmlAnchor.click();
									break;
								}
							}
						}
						// Crawler is over
						if(isEnd){
							if(results.size() == 0) {
								onCrawleLinstener.onFail("results is null");
								return;
							}
							onCrawleLinstener.onSuccess(results);
							break;
						}else{
							isEnd = true;
						}
					}
				}
				else if(collectType == CollectService.TYPE_CLUES) {
					// get absolute xpath
					List<String> mXpaths = produceXpaths(page, xpaths);
					if(mXpaths == null || mXpaths.size() == 0) {
						onCrawleLinstener.onFail("xpath is error");
						return;
					}
					// extract content
					List<String> results = getContent(page, mXpaths);
					if(results == null || results.size() == 0) {
						onCrawleLinstener.onFail("results is null");
						return;
					}
					onCrawleLinstener.onSuccess(results);
				}
				else if(collectType == CollectService.TYPE_CLUES_AJAX_CLICK) {
					List<String> results = new ArrayList<>();
					// click
					List<HtmlAnchor> htmlListItems = page.getByXPath(ajaxXpath);

					for(HtmlAnchor htmlAnchor : htmlListItems){
						htmlAnchor.click();
						// get absolute xpath
						List<String> mXpaths = produceXpaths(page, xpaths);
						// extract content
						results.addAll(getContent(page, mXpaths));
					}
					// Crawler is over
					if(results.size() == 0) {
						onCrawleLinstener.onFail("results is null");
						return;
					}
					onCrawleLinstener.onSuccess(results);
				}
			}catch (IOException e) {
				System.out.println(e.getMessage());
			}
		}

		/**
		 *
		 * @param page
		 * @param xpaths
		 * @return
		 */
		List<String> produceXpaths(HtmlPage page, List<String> xpaths) {
			int size = getNumber(page, xpaths);
			List<String> mXpaths = new ArrayList<>();
			String root = getXpathListTag(xpaths);
			String tmp = xpaths.get(0);
			assert root != null;
			int indexTmp = tmp.substring(root.length()).indexOf(']');

			// produce all xpath
			for(int index = 1; index <= size; index++) {
				StringBuilder sb = new StringBuilder();
				sb.append(getXpathListTag(xpaths))
						.append("[")
						.append(index)
						.append(tmp.substring(indexTmp + root.length()));
				mXpaths.add(sb.toString());
			}
			return mXpaths;
		}

		/**
		 *
		 * @param page
		 * @param xpaths
		 * @return
		 */
		List<String> getContent(HtmlPage page, List<String> xpaths) {
			List<String> results = new ArrayList<>();
			for(int index = 0; index < xpaths.size(); index++) {
				List<Object> byXPath = page.getByXPath(xpaths.get(index));
				if(byXPath.size() > 0) {
					Object o = byXPath.get(0);
					if( o instanceof HtmlTableDataCell) {
						results.add(((HtmlTableDataCell) o).getTextContent());
					}else if( o instanceof HtmlAnchor) {
						results.add(((HtmlAnchor) o).getTextContent());
					}else if( o instanceof HtmlArticle) {
						results.add(((HtmlArticle) o).getTextContent());
					}else if( o instanceof HtmlLink) {
						results.add(((HtmlLink) o).getTextContent());
					}
				}
			}
			return results;
		}

		/**
		 * 获取数量
		 * @param page
		 * @param xpaths
		 * @return
		 */
		int getNumber(HtmlPage page, List<String> xpaths) {
			String xpathAll = getXpathListTag(xpaths);
			if(xpathAll != null) {
				return page.getByXPath(xpathAll).size();
			}else {
				return 0;
			}
		}
		/**
		 * get the root tag according to xpaths
		 * @param xpaths
		 * @return the tag
		 */
		String getXpathListTag(List<String> xpaths) {
			if(xpaths.size() >= 2) {
				char[] mainXpath = xpaths.get(0).toCharArray();
				char[] secondXpath = xpaths.get(1).toCharArray();
				int minLength = Math.min(mainXpath.length, secondXpath.length);
				int index = 0;
				while(index < minLength && mainXpath[index] == secondXpath[index]){
					index++;
				}
				return xpaths.get(0).substring(0, index-1);

			}else {
				return null;
			}
		}
	}

	/**
	 * 测试　只需要两个xpath和一个url
	 * @param args
	 */
	public static void main(String[] args) {
		String xpath1 = "//*[@id=\"tableData_\"]/div[2]/table/tbody/tr[2]/td[2]";
		String xpath2 = "//*[@id=\"tableData_\"]/div[2]/table/tbody/tr[3]/td[2]";
		String url = "http://www.sse.com.cn/assortment/stock/list/share/";
		CollectService collectByCluesService = new CollectService();

		OnCrawleLinstener onCrawleLinstener = new OnCrawleLinstener() {
			@Override
			public void onSuccess(List<String> result) {
				System.out.println(result.size());
			}

			@Override
			public void onFail(String error) {

			}
		};
		String ajaxXpath = "//*[@id=\"idStr\"]";
		collectByCluesService.crawl(onCrawleLinstener, url, CollectService.TYPE_CLUES_AJAX_FLIP,
				ajaxXpath, xpath1, xpath2);

	}

	/**
	 * 线索 ＋ ajax翻页/点击
	 * @param onCrawleLinstener
	 * @param url
	 * @param ajaxXpath
	 * @param xpath1
	 * @param xpath2
	 */
	public void crawl(OnCrawleLinstener onCrawleLinstener, String url, int type, String ajaxXpath, String xpath1, String xpath2) {
		this.onCrawleLinstener = onCrawleLinstener;
		List<String> clues = new ArrayList<>();
		clues.add(xpath1);
		clues.add(xpath2);
		CollectProcessor clueAjaxProcessor = new CollectProcessor(url, type);
		clueAjaxProcessor.setXpaths(clues);
		clueAjaxProcessor.setAjaxXpath(ajaxXpath);
		clueAjaxProcessor.setOnCrawleLinstener(this.onCrawleLinstener);
		clueAjaxProcessor.start();
	}
	/**
	 * 线索
	 * @param onCrawleLinstener
	 * @param url
	 * @param xpath1
	 * @param xpath2
	 */
	public void crawl(OnCrawleLinstener onCrawleLinstener, String url, String xpath1, String xpath2) {
		this.onCrawleLinstener = onCrawleLinstener;
		List<String> clues = new ArrayList<>();
		clues.add(xpath1);
		clues.add(xpath2);
		CollectProcessor clueProcessor = new CollectProcessor(url, CollectService.TYPE_CLUES);
		clueProcessor.setXpaths(clues);
		clueProcessor.start();
		clueProcessor.setOnCrawleLinstener(this.onCrawleLinstener);
	}

	public OnCrawleLinstener getOnCrawleLinstener() {
		return onCrawleLinstener;
	}

	public void setOnCrawleLinstener(OnCrawleLinstener onCrawleLinstener) {
		this.onCrawleLinstener = onCrawleLinstener;
	}

	/**
	 * 使用回调获取结果
	 */
	public interface OnCrawleLinstener {
		void onSuccess(List<String> result);
		void onFail(String error);
	}
}
