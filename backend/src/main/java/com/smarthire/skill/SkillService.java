package com.smarthire.skill;

import com.smarthire.common.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class SkillService {

    private final SkillRepository skillRepository;

    @Transactional(readOnly = true)
    public List<Skill> list(String query) {
        List<Skill> all = skillRepository.findAllByOrderByCategoryAscNameAsc();
        if (query == null || query.isBlank()) {
            return all;
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        return all.stream()
                .filter(s -> s.getName().contains(needle) || s.getCategory().toLowerCase(Locale.ROOT).contains(needle))
                .toList();
    }

    @Transactional
    public Skill create(String name, String category) {
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        if (skillRepository.existsByNameIgnoreCase(normalized)) {
            throw new ConflictException("Skill already exists: " + normalized);
        }
        Skill skill = new Skill();
        skill.setName(normalized);
        skill.setCategory(category == null || category.isBlank() ? "OTHER" : category.trim().toUpperCase(Locale.ROOT));
        return skillRepository.save(skill);
    }
}
