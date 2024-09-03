package searchengine.utility;

import lombok.experimental.UtilityClass;
import searchengine.config.Site;
import searchengine.config.SitesList;

import java.util.List;

@UtilityClass
public class TransformString {

    public boolean checkPageSingleIndexingArgument(String page){
        List<Site> siteList = new SitesList().getSites();
        for (Site site : siteList){
            if(page.contains(site.getUrl())) {
                return true;
            }
        }
        return false;
    }
}
