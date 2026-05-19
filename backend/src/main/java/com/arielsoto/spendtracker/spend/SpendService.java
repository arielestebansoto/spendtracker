package com.arielsoto.spendtracker.spend;

import org.springframework.stereotype.Service;

import com.arielsoto.spendtracker.category.Category;
import com.arielsoto.spendtracker.category.CategoryRepository;
import com.arielsoto.spendtracker.user.UserApp;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SpendService {

    private final SpendRepository spendRepository;
    private final CategoryRepository categoryRepository;

    public SpendResponse create(
        CreateSpendRequest request,
        UserApp user
    ) {

        Category category = categoryRepository
            .findById(request.categoryId())
            .orElseThrow();

        Spend spend = Spend.builder()
            .user(user)
            .category(category)
            .description(request.description())
            .amount(request.amount())
            .currency(request.currency())
            .spendDate(request.spendDate())
            .build();

        Spend saved = spendRepository.save(spend);

        return new SpendResponse(
            saved.getId(),
            saved.getCategory().getId(),
            saved.getCategory().getName(),
            saved.getDescription(),
            saved.getAmount(),
            saved.getCurrency(),
            saved.getSpendDate()
        );
    }
}