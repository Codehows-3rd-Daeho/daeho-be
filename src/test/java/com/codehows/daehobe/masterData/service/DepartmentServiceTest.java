package com.codehows.daehobe.masterData.service;

import com.codehows.daehobe.masterData.dto.MasterDataDto;
import com.codehows.daehobe.masterData.entity.Department;
import com.codehows.daehobe.masterData.repository.DepartmentRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * <p>DepartmentService에 대한 단위 테스트 클래스입니다.</p>
 *
 * <p>MockitoExtension을 사용하여 Mockito 어노테이션을 활성화하고,
 * {@code @Mock}을 통해 `DepartmentRepository`를 Mock 객체로 주입하여
 * `DepartmentService`의 비즈니스 로직을 격리하여 테스트합니다.</p>
 */
@ExtendWith(MockitoExtension.class)
class DepartmentServiceTest {

    @Mock
    private DepartmentRepository departmentRepository;

    @InjectMocks
    private DepartmentService departmentService;

    // 테스트에서 사용할 공통 Department 객체
    private Department testDepartment;
    private Long departmentId = 1L;
    private String departmentName = "테스트부서";

    @BeforeEach
    void setUp() {
        testDepartment = Department.builder()
                .id(departmentId)
                .name(departmentName)
                .build();
    }

    /**
     * <p>ID를 통해 부서를 성공적으로 조회하는 테스트.</p>
     * <p><b>테스트 시나리오:</b></p>
     * <ol>
     *     <li>`departmentRepository.findById` 호출 시 `Optional.of(testDepartment)`를 반환하도록 Mocking.</li>
     *     <li>`getDepartmentById` 호출 후 반환된 부서가 예상한 `testDepartment`와 일치하는지 검증.</li>
     * </ol>
     */
    @Test
    @DisplayName("성공: ID로 부서 조회")
    void getDepartmentById_Success() {
        // given
        when(departmentRepository.findById(departmentId)).thenReturn(Optional.of(testDepartment));

        // when
        Department foundDepartment = departmentService.getDepartmentById(departmentId);

        // then
        assertThat(foundDepartment).isNotNull();
        assertThat(foundDepartment.getId()).isEqualTo(departmentId);
        assertThat(foundDepartment.getName()).isEqualTo(departmentName);
        verify(departmentRepository, times(1)).findById(departmentId);
    }

    /**
     * <p>ID로 부서 조회 시 부서를 찾을 수 없는 경우 `EntityNotFoundException` 발생 테스트.</p>
     * <p><b>테스트 시나리오:</b></p>
     * <ol>
     *     <li>`departmentRepository.findById` 호출 시 `Optional.empty()`를 반환하도록 Mocking.</li>
     *     <li>`getDepartmentById` 호출 시 `EntityNotFoundException`이 발생하는지 검증.</li>
     * </ol>
     */
    @Test
    @DisplayName("실패: ID로 부서 조회 시 부서 없음")
    void getDepartmentById_NotFound() {
        // given
        when(departmentRepository.findById(departmentId)).thenReturn(Optional.empty());

        // when & then
        assertThrows(EntityNotFoundException.class, () -> departmentService.getDepartmentById(departmentId));
        verify(departmentRepository, times(1)).findById(departmentId);
    }

    /**
     * <p>ID가 `null`일 때 부서 조회 시 `null`을 반환하는 테스트.</p>
     * <p><b>테스트 시나리오:</b></p>
     * <ol>
     *     <li>`getDepartmentById`에 `null` ID를 전달.</li>
     *     <li>`null`이 반환되는지 검증.</li>
     *     <li>`departmentRepository.findById`가 호출되지 않았는지 검증.</li>
     * </ol>
     */
    @Test
    @DisplayName("성공: ID가 null일 때 부서 조회")
    void getDepartmentById_NullId() {
        // when
        Department foundDepartment = departmentService.getDepartmentById(null);

        // then
        assertThat(foundDepartment).isNull();
        verify(departmentRepository, never()).findById(anyLong()); // findById가 호출되지 않아야 함
    }

    /**
     * <p>모든 부서를 성공적으로 조회하는 테스트.</p>
     * <p><b>테스트 시나리오:</b></p>
     * <ol>
     *     <li>`departmentRepository.findAll` 호출 시 두 개의 부서 리스트를 반환하도록 Mocking.</li>
     *     <li>`findAll` 호출 후 반환된 DTO 리스트의 크기와 내용이 예상과 일치하는지 검증.</li>
     * </ol>
     */
    @Test
    @DisplayName("성공: 모든 부서 조회")
    void findAll_Success() {
        // given
        Department dpt1 = Department.builder().id(1L).name("부서1").build();
        Department dpt2 = Department.builder().id(2L).name("부서2").build();
        List<Department> departmentList = Arrays.asList(dpt1, dpt2);

        when(departmentRepository.findAll()).thenReturn(departmentList);

        // when
        List<MasterDataDto> dtoList = departmentService.findAll();

        // then
        assertThat(dtoList).isNotNull();
        assertThat(dtoList).hasSize(2);
        assertThat(dtoList.get(0).getName()).isEqualTo("부서1");
        assertThat(dtoList.get(1).getName()).isEqualTo("부서2");
        verify(departmentRepository, times(1)).findAll();
    }

    /**
     * <p>부서 목록이 비어있을 때 모든 부서를 조회하는 테스트.</p>
     * <p><b>테스트 시나리오:</b></p>
     * <ol>
     *     <li>`departmentRepository.findAll` 호출 시 빈 리스트를 반환하도록 Mocking.</li>
     *     <li>`findAll` 호출 후 반환된 DTO 리스트가 비어있는지 검증.</li>
     * </ol>
     */
    @Test
    @DisplayName("성공: 부서 목록이 비어있을 때 모든 부서 조회")
    void findAll_EmptyList() {
        // given
        when(departmentRepository.findAll()).thenReturn(new ArrayList<>());

        // when
        List<MasterDataDto> dtoList = departmentService.findAll();

        // then
        assertThat(dtoList).isNotNull();
        assertThat(dtoList).isEmpty();
        verify(departmentRepository, times(1)).findAll();
    }

    /**
     * <p>새로운 부서를 성공적으로 생성하는 테스트.</p>
     * <p><b>테스트 시나리오:</b></p>
     * <ol>
     *     <li>`departmentRepository.existsByName` 호출 시 `false`를 반환하도록 Mocking (중복 없음).</li>
     *     <li>`departmentRepository.save` 호출 시 저장된 `Department` 객체를 반환하도록 Mocking.</li>
     *     <li>`createDpt` 호출 후 반환된 부서의 이름과 ID가 예상과 일치하는지 검증.</li>
     * </ol>
     */
    @Test
    @DisplayName("성공: 부서 생성")
    void createDpt_Success() {
        // given
        MasterDataDto newDptDto = new MasterDataDto(null, "새부서");
        Department savedDepartment = Department.builder().id(2L).name("새부서").build();

        when(departmentRepository.existsByName(newDptDto.getName())).thenReturn(false);
        when(departmentRepository.save(any(Department.class))).thenReturn(savedDepartment);

        // when
        Department createdDepartment = departmentService.createDpt(newDptDto);

        // then
        assertThat(createdDepartment).isNotNull();
        assertThat(createdDepartment.getId()).isEqualTo(2L);
        assertThat(createdDepartment.getName()).isEqualTo("새부서");
        verify(departmentRepository, times(1)).existsByName(newDptDto.getName());
        verify(departmentRepository, times(1)).save(any(Department.class));
    }

    /**
     * <p>이미 존재하는 이름으로 부서 생성 시 `IllegalArgumentException` 발생 테스트.</p>
     * <p><b>테스트 시나리오:</b></p>
     * <ol>
     *     <li>`departmentRepository.existsByName` 호출 시 `true`를 반환하도록 Mocking (중복 발생).</li>
     *     <li>`createDpt` 호출 시 `IllegalArgumentException`이 발생하는지 검증.</li>
     *     <li>`departmentRepository.save`가 호출되지 않았는지 검증.</li>
     * </ol>
     */
    @Test
    @DisplayName("실패: 중복 이름으로 부서 생성 시 IllegalArgumentException")
    void createDpt_DuplicateName_ThrowsException() {
        // given
        MasterDataDto newDptDto = new MasterDataDto(null, departmentName);
        when(departmentRepository.existsByName(departmentName)).thenReturn(true);

        // when & then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> departmentService.createDpt(newDptDto));
        assertThat(exception.getMessage()).contains("이미 존재하는 부서입니다");
        verify(departmentRepository, times(1)).existsByName(departmentName);
        verify(departmentRepository, never()).save(any(Department.class)); // save는 호출되면 안 됨
    }

    /**
     * <p>부서를 성공적으로 삭제하는 테스트.</p>
     * <p><b>테스트 시나리오:</b></p>
     * <ol>
     *     <li>`departmentRepository.findById` 호출 시 `Optional.of(testDepartment)`를 반환하도록 Mocking.</li>
     *     <li>`delete` 메소드 호출 시 아무것도 반환하지 않도록 Mocking.</li>
     *     <li>`deleteDpt` 호출 후 `departmentRepository.delete`가 정확히 한 번 호출되었는지 검증.</li>
     * </ol>
     */
    @Test
    @DisplayName("성공: 부서 삭제")
    void deleteDpt_Success() {
        // given
        when(departmentRepository.findById(departmentId)).thenReturn(Optional.of(testDepartment));
        doNothing().when(departmentRepository).delete(testDepartment);

        // when
        departmentService.deleteDpt(departmentId);

        // then
        verify(departmentRepository, times(1)).findById(departmentId);
        verify(departmentRepository, times(1)).delete(testDepartment);
    }

    /**
     * <p>삭제할 부서를 찾을 수 없을 때 `EntityNotFoundException` 발생 테스트.</p>
     * <p><b>테스트 시나리오:</b></p>
     * <ol>
     *     <li>`departmentRepository.findById` 호출 시 `Optional.empty()`를 반환하도록 Mocking.</li>
     *     <li>`deleteDpt` 호출 시 `EntityNotFoundException`이 발생하는지 검증.</li>
     *     <li>`departmentRepository.delete`가 호출되지 않았는지 검증.</li>
     * </ol>
     */
    @Test
    @DisplayName("실패: 삭제할 부서 없음 시 EntityNotFoundException")
    void deleteDpt_NotFound_ThrowsException() {
        // given
        when(departmentRepository.findById(departmentId)).thenReturn(Optional.empty());

        // when & then
        assertThrows(EntityNotFoundException.class, () -> departmentService.deleteDpt(departmentId));
        verify(departmentRepository, times(1)).findById(departmentId);
        verify(departmentRepository, never()).delete(any(Department.class)); // delete는 호출되면 안 됨
    }

    /**
     * <p>부서 이름을 성공적으로 업데이트하는 테스트.</p>
     * <p><b>테스트 시나리오:</b></p>
     * <ol>
     *     <li>`departmentRepository.findById` 호출 시 `Optional.of(testDepartment)`를 반환하도록 Mocking.</li>
     *     <li>`updateDpt` 호출 후 반환된 부서의 이름이 새 이름으로 변경되었는지 검증.</li>
     * </ol>
     */
    @Test
    @DisplayName("성공: 부서 이름 업데이트")
    void updateDpt_Success() {
        // given
        String newName = "업데이트부서";
        MasterDataDto updateDto = new MasterDataDto(departmentId, newName);
        when(departmentRepository.findById(departmentId)).thenReturn(Optional.of(testDepartment));
        // save는 transactional 때문에 실제 저장되므로 mocking 필요 없음, changeName 메소드가 entity 내부에서 동작

        // when
        Department updatedDepartment = departmentService.updateDpt(departmentId, updateDto);

        // then
        assertThat(updatedDepartment).isNotNull();
        assertThat(updatedDepartment.getName()).isEqualTo(newName);
        verify(departmentRepository, times(1)).findById(departmentId);
        // departmentRepository.save는 updateDpt 내부에서 명시적으로 호출되지 않으므로 verify 하지 않음.
        // @Transactional에 의해 flush 시점에 save가 일어남
    }

    /**
     * <p>업데이트할 부서를 찾을 수 없을 때 `EntityNotFoundException` 발생 테스트.</p>
     * <p><b>테스트 시나리오:</b></p>
     * <ol>
     *     <li>`departmentRepository.findById` 호출 시 `Optional.empty()`를 반환하도록 Mocking.</li>
     *     <li>`updateDpt` 호출 시 `EntityNotFoundException`이 발생하는지 검증.</li>
     * </ol>
     */
    @Test
    @DisplayName("실패: 업데이트할 부서 없음 시 EntityNotFoundException")
    void updateDpt_NotFound_ThrowsException() {
        // given
        MasterDataDto updateDto = new MasterDataDto(departmentId, "새이름");
        when(departmentRepository.findById(departmentId)).thenReturn(Optional.empty());

        // when & then
        assertThrows(EntityNotFoundException.class, () -> departmentService.updateDpt(departmentId, updateDto));
        verify(departmentRepository, times(1)).findById(departmentId);
    }
}
