package com.codehows.daehobe.issue.service;

import com.codehows.daehobe.common.constant.Status;
import com.codehows.daehobe.common.constant.TargetType;
import com.codehows.daehobe.file.entity.File;
import com.codehows.daehobe.file.service.FileService;
import com.codehows.daehobe.issue.dto.IssueFormDto;
import com.codehows.daehobe.issue.dto.IssueMemberDto;
import com.codehows.daehobe.issue.entity.Issue;
import com.codehows.daehobe.issue.entity.IssueDepartment;
import com.codehows.daehobe.issue.entity.IssueMember;
import com.codehows.daehobe.issue.repository.IssueRepository;
import com.codehows.daehobe.masterData.entity.Category;
import com.codehows.daehobe.masterData.entity.Department;
import com.codehows.daehobe.masterData.service.CategoryService;
import com.codehows.daehobe.masterData.service.SetNotificationService;
import com.codehows.daehobe.member.entity.Member;
import com.codehows.daehobe.masterData.dto.SetNotificationDto;
import com.codehows.daehobe.notification.service.NotificationService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IssueServiceTest {

    @Mock
    private IssueRepository issueRepository;
    @Mock
    private FileService fileService;
    @Mock
    private IssueDepartmentService issueDepartmentService;
    @Mock
    private IssueMemberService issueMemberService;
    @Mock
    private CategoryService categoryService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private SetNotificationService setNotificationService;

    @InjectMocks
    private IssueService issueService;

    private Issue testIssue;
    private Category testCategory;
    private Member testMember;
    private SetNotificationDto notificationSetting;

    @BeforeEach
    void setUp() {
        testCategory = Category.builder().id(1L).name("테스트 카테고리").build();
        testMember = Member.builder().id(1L).name("테스트 작성자").build();
        testIssue = Issue.builder()
                .id(1L)
                .title("테스트 이슈")
                .content("테스트 내용")
                .status(Status.IN_PROGRESS)
                .category(testCategory)
                .issueMembers(Collections.singletonList(
                        IssueMember.builder()
                            .issue(testIssue)
                            .member(testMember)
                            .isHost(true)
                            .isRead(false)
                            .isPermitted(true)
                            .build())
                )
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(7))
                .isDel(false)
                .isPrivate(false) // 빌더를 통해 isPrivate 설정
                .build();
        notificationSetting = new SetNotificationDto(true, true, true, true, true);
    }

    @Nested
    @DisplayName("이슈 생성 테스트")
    class CreateIssueTests {

        @Test
        @DisplayName("성공: 이슈 생성 (파일, 부서, 참여자, 알림 포함)")
        void createIssue_Success_FullScenario() {
            // given
            IssueFormDto formDto = IssueFormDto.builder()
                    .title(testIssue.getTitle())
                    .content(testIssue.getContent())
                    .status(testIssue.getStatus().name())
                    .categoryId(testIssue.getCategory().getId())
                    .departmentIds(Collections.singletonList(1L))
                    .members(Collections.singletonList(IssueMemberDto.builder()
                            .id(testMember.getId())
                            .host(true)
                            .permitted(true)
                            .read(false) // 필요한 모든 필드 채움
                            .build()))
                    .build();
            List<MultipartFile> files = Collections.singletonList(new MockMultipartFile("file", "test.txt", "text/plain", "test data".getBytes()));
            String writerId = "1";

            when(categoryService.getCategoryById(anyLong())).thenReturn(testCategory);
            when(issueRepository.save(any(Issue.class))).thenReturn(testIssue);
            when(setNotificationService.getSetting()).thenReturn(notificationSetting);
            when(issueDepartmentService.saveDepartment(anyLong(), anyList())).thenReturn(List.of(new IssueDepartment()));
            when(issueMemberService.saveIssueMember(anyLong(), anyList())).thenReturn(List.of(new IssueMember()));
            when(fileService.uploadFiles(anyLong(), anyList(), any())).thenReturn(List.of(new File()));
            doNothing().when(notificationService).notifyMembers(anyList(), anyLong(), anyString(), anyString());

            // when
            Issue createdIssue = issueService.createIssue(formDto, files, writerId);

            // then
            assertThat(createdIssue.getTitle()).isEqualTo(testIssue.getTitle());
            verify(issueRepository).save(any(Issue.class));
            verify(issueDepartmentService).saveDepartment(eq(testIssue.getId()), eq(formDto.getDepartmentIds()));
            verify(issueMemberService).saveIssueMember(eq(testIssue.getId()), eq(formDto.getMembers()));
            verify(fileService).uploadFiles(eq(testIssue.getId()), eq(files), eq(TargetType.ISSUE));
            verify(notificationService).notifyMembers(anyList(), eq(Long.valueOf(writerId)), anyString(), anyString());
        }

        @Test
        @DisplayName("성공: 이슈 생성 (파일 없음, 부서/참여자/알림 없음)")
        void createIssue_Success_MinimalScenario() {
            // given
            IssueFormDto formDto = IssueFormDto.builder()
                    .title(testIssue.getTitle())
                    .content(testIssue.getContent())
                    .status(Status.IN_PROGRESS.name())
                    .categoryId(testIssue.getCategory().getId())
                    .startDate(LocalDate.now())
                    .endDate(LocalDate.now().plusDays(1))
                    .isDel(false)
                    .isPrivate(false)
                    .build(); // No departments, members, files
            List<MultipartFile> files = null;
            String writerId = "1";

            when(categoryService.getCategoryById(1L)).thenReturn(testCategory);
            when(issueRepository.save(any(Issue.class))).thenReturn(testIssue);
            when(setNotificationService.getSetting()).thenReturn(notificationSetting);


            // when
            Issue createdIssue = issueService.createIssue(formDto, files, writerId);

            // then
            assertThat(createdIssue.getTitle()).isEqualTo(testIssue.getTitle());
            verify(issueRepository).save(any(Issue.class));
            verify(issueDepartmentService, never()).saveDepartment(anyLong(), anyList());
            verify(issueMemberService, never()).saveIssueMember(anyLong(), anyList());
            verify(fileService, never()).uploadFiles(anyLong(), anyList(), any());
            verify(notificationService, never()).notifyMembers(anyList(), anyLong(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("이슈 상세 조회 테스트")
    class GetIssueDtlTests {
        private IssueMember testHost;
        private IssueMember testParticipant;
        private Department testDepartment;
        private List<IssueDepartment> issueDepartments;
        private List<IssueMember> issueMembers;

        @BeforeEach
        void setupDetail() {
            Member hostMember = Member.builder().id(2L).name("호스트").build();
            Member participantMember = Member.builder().id(3L).name("참여자").build();
            testHost = IssueMember.builder().issue(testIssue).member(hostMember).isHost(true).isPermitted(true).build();
            testParticipant = IssueMember.builder().issue(testIssue).member(participantMember).isHost(false).isPermitted(true).build();

            testDepartment = Department.builder().id(1L).name("개발부").build();
            issueDepartments = Collections.singletonList(IssueDepartment.builder().issue(testIssue).department(testDepartment).build());
            issueMembers = Arrays.asList(testHost, testParticipant);

            // Create a new Issue object with the collections already set
            testIssue = Issue.builder()
                    .id(1L)
                    .title("테스트 이슈")
                    .content("테스트 내용")
                    .status(Status.IN_PROGRESS)
                    .category(testCategory)
                    .startDate(LocalDate.now())
                    .endDate(LocalDate.now().plusDays(7))
                    .isDel(false)
                    .isPrivate(false)
                    .issueMembers(issueMembers) // Collections set via builder
                    .build();
        }

        @Test
        @DisplayName("성공: 공개 이슈 상세 조회")
        void getIssueDtl_PublicIssue_Success() {
            // given
            IssueFormDto privateFormDto = IssueFormDto.builder()
                    .title(testIssue.getTitle())
                    .content(testIssue.getContent())
                    .status(testIssue.getStatus().name())
                    .categoryId(testIssue.getCategory().getId())
                    .startDate(testIssue.getStartDate())
                    .endDate(testIssue.getEndDate())
                    .isDel(testIssue.isDel())
                    .isPrivate(false) // 공개 이슈
                    .build();
            testIssue.update(privateFormDto, testCategory);
            when(issueRepository.findDetailById(anyLong())).thenReturn(Optional.of(testIssue));
            when(fileService.getIssueFiles(anyLong())).thenReturn(Collections.emptyList());
            when(issueDepartmentService.getDepartmentName(any(Issue.class))).thenReturn(Collections.singletonList("개발부"));

            // when
            issueService.getIssueDtl(testIssue.getId(), testMember.getId());

            // then
            verify(issueRepository).findDetailById(testIssue.getId());
            verify(fileService).getIssueFiles(testIssue.getId());
            verify(issueDepartmentService).getDepartmentName(testIssue);
        }

        @Test
        @DisplayName("성공: 비밀 이슈 상세 조회 (참여자)")
        void getIssueDtl_PrivateIssue_Participant_Success() {
            // given
            IssueFormDto privateFormDto = IssueFormDto.builder()
                    .title(testIssue.getTitle())
                    .content(testIssue.getContent())
                    .status(testIssue.getStatus().name())
                    .categoryId(testIssue.getCategory().getId())
                    .startDate(testIssue.getStartDate())
                    .endDate(testIssue.getEndDate())
                    .isDel(testIssue.isDel())
                    .isPrivate(true) // 비밀 이슈
                    .build();
            testIssue.update(privateFormDto, testCategory);
            when(issueRepository.findDetailById(anyLong())).thenReturn(Optional.of(testIssue));
            when(fileService.getIssueFiles(anyLong())).thenReturn(Collections.emptyList());
            when(issueDepartmentService.getDepartmentName(any(Issue.class))).thenReturn(Collections.singletonList("개발부"));

            // when
            issueService.getIssueDtl(testIssue.getId(), testParticipant.getMember().getId()); // 참여자로 조회

            // then
            verify(issueRepository).findDetailById(testIssue.getId());
            verify(fileService).getIssueFiles(testIssue.getId());
            verify(issueDepartmentService).getDepartmentName(testIssue);
        }

        @Test
        @DisplayName("실패: 비밀 이슈 상세 조회 (비참여자) - RuntimeException")
        void getIssueDtl_PrivateIssue_NonParticipant_Failure() {
            // given
            IssueFormDto privateFormDto = IssueFormDto.builder()
                    .title(testIssue.getTitle())
                    .content(testIssue.getContent())
                    .status(testIssue.getStatus().name())
                    .categoryId(testIssue.getCategory().getId())
                    .startDate(testIssue.getStartDate())
                    .endDate(testIssue.getEndDate())
                    .isDel(testIssue.isDel())
                    .isPrivate(true) // 비밀 이슈
                    .build();
            testIssue.update(privateFormDto, testCategory);
            Long nonParticipantId = 99L;
            when(issueRepository.findDetailById(anyLong())).thenReturn(Optional.of(testIssue));

            // when & then
            RuntimeException exception = assertThrows(RuntimeException.class,
                    () -> issueService.getIssueDtl(testIssue.getId(), nonParticipantId));
            assertThat(exception.getMessage()).isEqualTo("권한이 없는 비밀글입니다.");
        }

        @Test
        @DisplayName("실패: 이슈 상세 조회 (이슈 없음) - RuntimeException")
        void getIssueDtl_IssueNotFound_Failure() {
            // given
            when(issueRepository.findDetailById(anyLong())).thenReturn(Optional.empty());

            // when & then
            assertThrows(RuntimeException.class, () -> issueService.getIssueDtl(99L, testMember.getId()));
        }
    }

    @Nested
    @DisplayName("이슈 읽음 상태 업데이트 테스트")
    class UpdateReadStatusTests {
        private IssueMember issueMemberToUpdate;

        @BeforeEach
        void setupUpdateReadStatus() {
            issueMemberToUpdate = IssueMember.builder().issue(testIssue).member(testMember).isRead(false).build();
        }

        @Test
        @DisplayName("성공: 이슈 읽음 상태 업데이트 (isRead가 false일 때)")
        void updateReadStatus_WhenFalse_Success() {
            // given
            when(issueMemberService.findByIssueIdAndMemberId(testIssue.getId(), testMember.getId()))
                    .thenReturn(issueMemberToUpdate);

            // when
            IssueMember after = issueService.updateReadStatus(testIssue.getId(), testMember.getId());

            // then
            assertThat(after.isRead()).isTrue();
        }

        @Test
        @DisplayName("성공: 이슈 읽음 상태 업데이트 (isRead가 이미 true일 때)")
        void updateReadStatus_WhenTrue_NoChange() {
            // given
            issueMemberToUpdate.updateIsRead(true); // 이미 읽음 상태
            when(issueMemberService.findByIssueIdAndMemberId(testIssue.getId(), testMember.getId()))
                    .thenReturn(issueMemberToUpdate);

            // when
            IssueMember after = issueService.updateReadStatus(testIssue.getId(), testMember.getId());

            // then
            assertThat(after.isRead()).isTrue();
        }
    }


    @Nested
    @DisplayName("이슈 업데이트 테스트")
    class UpdateIssueTests {
        private IssueFormDto updateFormDto;
        private List<MultipartFile> newFiles;
        private List<Long> removeFileIds;
        private String writerId = "1";

        @BeforeEach
        void setupUpdateIssue() {
            updateFormDto = IssueFormDto.builder()
                    .title("수정된 이슈")
                    .content("수정된 내용")
                    .status(Status.COMPLETED.name()) // 상태 변경
                    .categoryId(1L)
                    .startDate(LocalDate.now())
                    .endDate(LocalDate.now().plusDays(10))
                    .isDel(false)
                    .isPrivate(false)
                    .departmentIds(Collections.singletonList(2L))
                    .members(Collections.singletonList(IssueMemberDto.builder()
                            .id(2L).host(true).permitted(true).read(false) // 필요한 모든 필드 채움
                            .build()))
                    .build();
            newFiles = Collections.singletonList(new MockMultipartFile("newFile", "new.txt", "text/plain", "new data".getBytes()));
            removeFileIds = Collections.singletonList(1L);
        }

        @Test
        @DisplayName("성공: 이슈 업데이트 (파일 변경 포함, 상태 변경 없음)")
        void updateIssue_Success_WithFileChanges_NoStatusChange() {
            // given
            IssueFormDto initialStatusFormDto = IssueFormDto.builder()
                    .title(testIssue.getTitle())
                    .content(testIssue.getContent())
                    .status(Status.IN_PROGRESS.name())
                    .categoryId(testIssue.getCategory().getId())
                    .startDate(testIssue.getStartDate())
                    .endDate(testIssue.getEndDate())
                    .isDel(testIssue.isDel())
                    .isPrivate(testIssue.isPrivate())
                    .departmentIds(Collections.singletonList(2L))
                    .members(Collections.singletonList(IssueMemberDto.builder().id(testMember.getId()).build()))
                    .build();
            testIssue.update(initialStatusFormDto, testCategory); // 기존 상태를 In_Progress로 설정

            updateFormDto = IssueFormDto.builder() // updateFormDto도 In_Progress 상태로 설정
                    .title(updateFormDto.getTitle())
                    .content(updateFormDto.getContent())
                    .status(Status.IN_PROGRESS.name())
                    .host(updateFormDto.getHost())
                    .categoryId(updateFormDto.getCategoryId())
                    .startDate(updateFormDto.getStartDate())
                    .endDate(updateFormDto.getEndDate())
                    .departmentIds(updateFormDto.getDepartmentIds())
                    .members(updateFormDto.getMembers())
                    .isDel(updateFormDto.getIsDel())
                    .isPrivate(updateFormDto.getIsPrivate())
                    .build();
        }

        @Test
        @DisplayName("성공: 이슈 업데이트 (상태 변경 포함, 알림 발송)")
        void updateIssue_Success_WithStatusChange_SendsNotification() {
            // given
            IssueFormDto initialStatusFormDto = IssueFormDto.builder()
                    .title(testIssue.getTitle())
                    .content(testIssue.getContent())
                    .status(Status.IN_PROGRESS.name()) // 기존 상태
                    .categoryId(testIssue.getCategory().getId())
                    .startDate(testIssue.getStartDate())
                    .endDate(testIssue.getEndDate())
                    .isDel(testIssue.isDel())
                    .isPrivate(testIssue.isPrivate())
                    .departmentIds(Collections.singletonList(2L))
                    .members(Collections.singletonList(IssueMemberDto.builder().id(testMember.getId()).build()))
                    .build();
            testIssue.update(initialStatusFormDto, testCategory); // 기존 상태

            updateFormDto = IssueFormDto.builder() // updateFormDto의 상태를 변경된 상태로 설정
                    .title(updateFormDto.getTitle())
                    .content(updateFormDto.getContent())
                    .status(Status.COMPLETED.name()) // 변경된 상태
                    .host(updateFormDto.getHost())
                    .categoryId(updateFormDto.getCategoryId())
                    .startDate(updateFormDto.getStartDate())
                    .endDate(updateFormDto.getEndDate())
                    .departmentIds(updateFormDto.getDepartmentIds())
                    .members(updateFormDto.getMembers())
                    .isDel(updateFormDto.getIsDel())
                    .isPrivate(updateFormDto.getIsPrivate())
                    .build();
        }

        @Test
        @DisplayName("실패: 이슈 업데이트 (이슈 없음) - EntityNotFoundException")
        void updateIssue_IssueNotFound_Failure() {
            // given
            when(issueRepository.findById(anyLong())).thenReturn(Optional.empty());

            // when & then
            assertThrows(EntityNotFoundException.class,
                    () -> issueService.updateIssue(99L, updateFormDto, newFiles, removeFileIds, writerId));
        }
    }

    @Nested
    @DisplayName("이슈 삭제 테스트")
    class DeleteIssueTests {
        @Test
        @DisplayName("성공: 이슈 삭제")
        void deleteIssue_Success() {
            // given
            when(issueRepository.findById(anyLong())).thenReturn(Optional.of(testIssue));

            // when
            Issue deletedIssue = issueService.deleteIssue(testIssue.getId());

            // then
            assertThat(deletedIssue.isDel()).isTrue(); // 논리적 삭제 확인
            verify(issueRepository).findById(testIssue.getId());
        }

        @Test
        @DisplayName("실패: 이슈 삭제 (이슈 없음) - EntityNotFoundException")
        void deleteIssue_NotFound_Failure() {
            // given
            when(issueRepository.findById(anyLong())).thenReturn(Optional.empty());

            // when & then
            assertThrows(EntityNotFoundException.class, () -> issueService.deleteIssue(99L));
        }
    }
}
