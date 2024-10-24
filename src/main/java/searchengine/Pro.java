package searchengine;

import org.jsoup.Jsoup;
import org.apache.lucene.morphology.russian.RussianMorphology;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import jakarta.transaction.Transactional;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import searchengine.repository.PageRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.IndexRepository;
import searchengine.repository.SiteRepository;
import searchengine.model.Page;
import searchengine.model.Lemma;
import searchengine.model.Index;

@Service
public class Pro {

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private LemmaRepository lemmaRepository;

    @Autowired
    private IndexRepository indexRepository;

    @Autowired
    private SiteRepository siteRepository;

    private final RussianMorphology morphology;

    public Pro() throws Exception {
        this.morphology = new RussianMorphology();
    }

    @Transactional
    public void processPage(String url, int siteId) throws Exception {
        // 1. Получаем HTML содержимое страницы
        String html = Jsoup.connect(url).ignoreHttpErrors(true).ignoreContentType(true).get().html();

        // 2. Извлекаем текст и удаляем теги
        String text = Jsoup.parse(html).text();

        // 3. Выполняем лемматизацию текста и получаем частотный словарь
        Map<String, Integer> lemmaCountMap = extractLemmas(text);

        // 4. Сохраняем страницу в таблицу page
        Page page = new Page(siteRepository.findById(siteId).orElseThrow(), url, 200, html);
        pageRepository.save(page);

        // 5. Обрабатываем каждую лемму
        for (Map.Entry<String, Integer> entry : lemmaCountMap.entrySet()) {
            String lemmaText = entry.getKey();
            int count = entry.getValue();

            // Проверяем, существует ли лемма в базе
            Lemma lemma = lemmaRepository.findByLemma(lemmaText);
            if (lemma == null) {
                // Если леммы нет, создаем новую
                lemma = new Lemma();
                lemma.setLemma(lemmaText);
                lemma.setFrequency(1);
            } else {
                // Если лемма существует, увеличиваем её частоту
                lemma.setFrequency(lemma.getFrequency() + 1);
            }
            lemmaRepository.save(lemma);

            // Создаем запись в таблице index (индексация)
            Index index = new Index();
            index.setPage(page); // Устанавливаем объект Page
            index.setLemma(lemma); // Устанавливаем объект Lemma
            index.setRank(count);
            indexRepository.save(index);
        }
    }

    // Метод для лемматизации текста и подсчета количества каждой леммы
    private Map<String, Integer> extractLemmas(String text) {
        Map<String, Integer> lemmaCountMap = new HashMap<>();
        List<String> words = List.of(text.split("\\P{IsCyrillic}+"));

        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }

            // Получаем леммы для каждого слова
            List<String> lemmas = morphology.getNormalForms(word.toLowerCase());

            for (String lemma : lemmas) {
                lemmaCountMap.put(lemma, lemmaCountMap.getOrDefault(lemma, 0) + 1);
            }
        }

        return lemmaCountMap;
    }
}