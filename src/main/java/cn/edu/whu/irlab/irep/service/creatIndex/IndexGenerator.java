package cn.edu.whu.irlab.irep.service.creatIndex;

import cn.edu.whu.irlab.irep.entity.Doc;
import cn.edu.whu.irlab.irep.entity.FullIndex;
import cn.edu.whu.irlab.irep.entity.InvertedIndex;
import cn.edu.whu.irlab.irep.entity.Record;
import cn.edu.whu.irlab.irep.service.impl.FullIndexServiceImpl;
import cn.edu.whu.irlab.irep.service.impl.InvertedIndexServiceImpl;
import cn.edu.whu.irlab.irep.service.impl.RecordServiceImpl;
import cn.edu.whu.irlab.irep.service.preProcess.PreProcessor;
import cn.edu.whu.irlab.irep.service.util.IndexTypeConstructor;
import org.apache.lucene.analysis.Analyzer;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author gcr
 * @version 1.0
 * @date 2019-06-17 15:59
 * @desc 生成倒排索引
 **/

public class IndexGenerator {


    @Autowired
    public PreProcessor preProcessor;

    @Autowired
    public RecordServiceImpl recordService;

    @Autowired
    public InvertedIndexServiceImpl invertedIndexService;

    @Autowired
    public FullIndexServiceImpl fullIndexService;

    @Autowired
    public IndexTypeConstructor indexTypeConstructor;

    private String folderPath;//包含待处理的文档的文件夹路径

    private String analyzerName;//分词器名

    private boolean isRemoveStopWord;//是否去停用词

    private String indexType;//索引类型

    /**
     * 构造方法
     * @param folderPath 包含待处理的文档的文件夹路径
     * @param analyzerName 分词器名
     * @param isRemoveStopWord 是否去停用词
     *
     * */
    public IndexGenerator(String folderPath, String analyzerName, boolean isRemoveStopWord) {
        this.folderPath = folderPath;
        this.analyzerName = analyzerName;
        this.isRemoveStopWord = isRemoveStopWord;
        this.indexType = indexTypeConstructor.indexTypeConstructor(analyzerName, isRemoveStopWord);
    }

    /**
     * 生成索引数据
     * */
    public void generateIndex(){
        createRecords();
        createInvertedIndex();
        createFullIndex();
    }

    /**
     * 生成records数据并插入records表中
     * */
    public void createRecords() {
        File fileFolder = new File(folderPath);

        if (fileFolder.exists()) {
            File[] files = fileFolder.listFiles();
            for (File file :
                    files) {
                Doc doc = new Doc(file.getPath(), file.getName());
                ArrayList<String> termList = doc.preProcess(analyzerName, isRemoveStopWord);
                int num = 0;
                for (String s :
                        termList) {
                    Record record = new Record();
                    record.setTerm(s);
                    record.setDocId(doc.getId());
                    record.setLocation(num);
                    record.setIndexType(indexType);
                    System.out.println(record.toString());
                    recordService.insert(record);
                    num++;
                }
            }
        }
    }

    /**
     * 聚合records中的数据，生成inverted_index中的数据
     * */
    public void createInvertedIndex() {

        //获取Records表中的数据
        List<Record> recordList;
        recordList=recordService.selectByIndexType(indexType);
        System.out.println("成功获取到records数据："+recordList.size()+"条");

        Map<String,List<Record>> termId_Record=new HashMap<>();
        String termID;

        //按照term和doc_id聚合record
        for (Record r:
                recordList) {
            List<Record> records=new ArrayList<>();
            termID=r.getTerm()+r.getDocId();
            if (termId_Record.containsKey(termID)){
                records=termId_Record.get(termID);
                records.add(r);
                termId_Record.put(termID,records);
            }else {
                records.add(r);
                termId_Record.put(termID,records);
            }
        }
        System.out.println("按照term和doc_id聚合record后有"+termId_Record.size()+"条数据");
        for (String s:
                termId_Record.keySet()) {
            String locations="";
            Record tempRecord=termId_Record.get(s).get(0);
            InvertedIndex invertedIndex=new InvertedIndex();
            invertedIndex.setDocId(tempRecord.getDocId());
            invertedIndex.setIndexType(tempRecord.getIndexType());
            invertedIndex.setTerm(tempRecord.getTerm());
            invertedIndex.setTf(termId_Record.get(s).size());
            //构造locations
            for (Record r:
                    termId_Record.get(s)) {
                locations+=r.getLocation()+":";
            }
            locations=locations.substring(0,locations.length()-1);
            invertedIndex.setLocations(locations);
            invertedIndexService.insert(invertedIndex);
        }
    }

    /**
     * 聚合inverted_index中的数据，生成full_index中的数据
     * */
    public void createFullIndex(){

        //获取inverted_index中的数据
        List<InvertedIndex> invertedIndexList;
        invertedIndexList = invertedIndexService.selectByIndexType(indexType);
        System.out.println("成功获取到inverted_index中的数据"+invertedIndexList.size()+"条");
        Map<String, List<InvertedIndex>> termInvertedIndex = new HashMap<>();
        String term;

        //按term聚合inverted_index
        for (InvertedIndex i :
                invertedIndexList) {
            List<InvertedIndex> invertedIndexes = new ArrayList<>();
            term = i.getTerm();
            if (termInvertedIndex.containsKey(term)) {
                invertedIndexes = termInvertedIndex.get(term);
                invertedIndexes.add(i);
                termInvertedIndex.put(term, invertedIndexes);
            } else {
                invertedIndexes.add(i);
                termInvertedIndex.put(term, invertedIndexes);
            }
        }
        System.out.println("按照term聚合inverted_index后有"+termInvertedIndex.size()+"条数据");
        for (String s :
                termInvertedIndex.keySet()) {
            String ids = "";
            InvertedIndex tempInvertedIndex = termInvertedIndex.get(s).get(0);
            FullIndex fullIndex = new FullIndex();
            fullIndex.setTerm(tempInvertedIndex.getTerm());
            fullIndex.setIndexType(tempInvertedIndex.getIndexType());
            fullIndex.setDf(termInvertedIndex.get(s).size());
            //构造ids
            for (InvertedIndex i :
                    termInvertedIndex.get(s)) {
                ids+=i.getDocId()+":";
            }
            ids=ids.substring(0,ids.length()-1);
            fullIndex.setIds(ids);
            fullIndexService.insert(fullIndex);
        }
    }
}
