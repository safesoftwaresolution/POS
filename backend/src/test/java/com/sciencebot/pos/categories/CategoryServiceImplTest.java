package com.sciencebot.pos.categories;

import com.sciencebot.pos.categories.internal.entities.Category;
import com.sciencebot.pos.categories.internal.repositories.CategoryRepository;
import com.sciencebot.pos.categories.internal.repositories.SubcategoryRepository;
import com.sciencebot.pos.categories.internal.services.CategoryServiceImpl;
import com.sciencebot.pos.categories.internal.mappers.CategoryMapper;
import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CategoryServiceImplTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private SubcategoryRepository subcategoryRepository;

    @Mock
    private CategoryDeleteValidator deleteValidator;

    @Mock
    private CategoryProductCountProvider countProvider;

    @Mock
    private CategoryMapper categoryMapper;

    private CategoryServiceImpl categoryService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        categoryService = new CategoryServiceImpl(
                categoryRepository,
                subcategoryRepository,
                List.of(deleteValidator),
                categoryMapper
        );
    }


    @Test
    void createCategory_Success() {
        CreateCategoryCommand command = new CreateCategoryCommand("Bebidas", "Bebidas frías");
        when(categoryRepository.existsByNameIgnoreCase("Bebidas")).thenReturn(false);

        Category saved = new Category();
        saved.setId(1L);
        saved.setName("Bebidas");
        saved.setDescription("Bebidas frías");
        when(categoryRepository.save(any(Category.class))).thenReturn(saved);

        CategoryDto expectedDto = new CategoryDto(1L, "Bebidas", "Bebidas frías", 0);
        when(categoryMapper.toDto(any(Category.class))).thenReturn(expectedDto);

        CategoryDto result = categoryService.createCategory(command);


        assertNotNull(result);
        assertEquals(1L, result.id());
        assertEquals("Bebidas", result.name());
        verify(categoryRepository, times(1)).save(any(Category.class));
    }

    @Test
    void createCategory_DuplicateName_ThrowsException() {
        CreateCategoryCommand command = new CreateCategoryCommand("bebidas", "Description");
        when(categoryRepository.existsByNameIgnoreCase("bebidas")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> categoryService.createCategory(command));
        assertTrue(ex.getMessage().contains("Ya existe"));
    }

    @Test
    void deleteCategory_ActiveProductsAssociated_ThrowsException() {
        when(categoryRepository.existsById(1L)).thenReturn(true);
        doThrow(new IllegalStateException("Tiene productos asociados")).when(deleteValidator).validateBeforeDelete(1L);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> categoryService.deleteCategory(1L));
        assertTrue(ex.getMessage().contains("productos asociados"));
        verify(categoryRepository, never()).deleteById(anyLong());
    }

    @Test
    void updateCategory_SameName_Success() {
        UpdateCategoryCommand command = new UpdateCategoryCommand("Bebidas", "Nueva descripción");
        Category existing = new Category();
        existing.setId(1L);
        existing.setName("Bebidas");
        existing.setDescription("Antigua descripción");

        when(categoryRepository.findById(1L)).thenReturn(java.util.Optional.of(existing));
        when(categoryRepository.findByNameIgnoreCase("Bebidas")).thenReturn(java.util.Optional.of(existing));
        when(categoryRepository.save(any(Category.class))).thenReturn(existing);

        CategoryDto expectedDto = new CategoryDto(1L, "Bebidas", "Nueva descripción", 0);
        when(categoryMapper.toDto(any(Category.class))).thenReturn(expectedDto);

        CategoryDto result = categoryService.updateCategory(1L, command);

        assertNotNull(result);
        assertEquals("Bebidas", result.name());
        verify(categoryRepository, times(1)).save(any(Category.class));
    }

    @Test
    void updateCategory_DuplicateNameDifferentId_ThrowsException() {
        UpdateCategoryCommand command = new UpdateCategoryCommand("Snacks", "Descripción");
        Category existing = new Category();
        existing.setId(1L);
        existing.setName("Bebidas");

        Category otherCategory = new Category();
        otherCategory.setId(2L);
        otherCategory.setName("Snacks");

        when(categoryRepository.findById(1L)).thenReturn(java.util.Optional.of(existing));
        when(categoryRepository.findByNameIgnoreCase("Snacks")).thenReturn(java.util.Optional.of(otherCategory));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> categoryService.updateCategory(1L, command));

        assertTrue(ex.getMessage().contains("Ya existe una categoría"));
        verify(categoryRepository, never()).save(any(Category.class));
    }
}
