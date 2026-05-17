package kpi.pavlenko.shvets.coursework.controller;

import kpi.pavlenko.shvets.coursework.entity.ProtocolDocument;
import kpi.pavlenko.shvets.coursework.repository.StaffRepository;
import kpi.pavlenko.shvets.coursework.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize; // Новий імпорт
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/medical")
@PreAuthorize("hasRole('DOCTOR')") // Захист на рівні бекенду
public class MedicalCardController {
    @Autowired private MedicalCardService medicalCardService;
    @Autowired private PatientService patientService;
    @Autowired private AppointmentService appointmentService;
    @Autowired private StaffService staffService;
    @Autowired private StaffRepository staffRepository;
    @Autowired private DocumentService documentService;

    @GetMapping("/patient/{patientId}")
    public String patientCard(@PathVariable Long patientId, Model model){
        model.addAttribute("patient", patientService.findById(patientId));
        model.addAttribute("protocols", medicalCardService.getProtocolsForPatient(patientId));
        model.addAttribute("doctors", staffService.findDoctors());
        return "medical/card";
    }

    @PostMapping("/protocol/{id}/documents")
    public String uploadDocument(@PathVariable Long id,
                                 @RequestParam("file") MultipartFile file,
                                 RedirectAttributes flash) {
        try {
            documentService.upload(id, file);
            flash.addFlashAttribute("success", "Файл завантажено: " + file.getOriginalFilename());
        } catch (Exception e) {
            flash.addFlashAttribute("error", "Помилка завантаження: " + e.getMessage());
        }
        return "redirect:/medical/protocol/" + id;
    }

    @GetMapping("/protocol/{id}/documents/{docId}")
    @ResponseBody
    public ResponseEntity<Resource> viewDocument(@PathVariable Long id,
                                                 @PathVariable Long docId) {
        try {
            ProtocolDocument doc = documentService.getById(docId);
            Resource resource = documentService.loadAsResource(docId);

            String disposition = "inline; filename=\"" + doc.getOriginalName() + "\"";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                    .contentType(MediaType.parseMediaType(doc.getContentType()))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/protocol/{id}/documents/{docId}/delete")
    public String deleteDocument(@PathVariable Long id,
                                 @PathVariable Long docId,
                                 RedirectAttributes flash) {
        try {
            documentService.delete(docId);
            flash.addFlashAttribute("success", "Файл видалено.");
        } catch (Exception e) {
            flash.addFlashAttribute("error", "Помилка видалення: " + e.getMessage());
        }
        return "redirect:/medical/protocol/" + id;
    }



    @GetMapping("/protocol/{id}")
    public String protocolView(@PathVariable Long id, Model model){
        model.addAttribute("protocol", medicalCardService.getProtocol(id));
        model.addAttribute("protocolDiagnoses", medicalCardService.getDiagnosesForProtocol(id));
        model.addAttribute("protocolTherapies", medicalCardService.getTherapiesForProtocol(id));
        model.addAttribute("allDiagnoses", medicalCardService.getAllDiagnoses());
        model.addAttribute("allTherapies", medicalCardService.getAllTherapies());
        model.addAttribute("documents", documentService.getForProtocol(id));
        return "medical/protocol";
    }

    @PostMapping("/patient/{patientId}/protocol/new")
    public String createProtocol(@PathVariable Long patientId,
                                 @RequestParam Long staffId,
                                 RedirectAttributes flash) {
        medicalCardService.createProtocol(patientId, staffId);
        flash.addFlashAttribute("success", "Protocol created successfully");
        return "redirect:/medical/patient/" + patientId;
    }

    @PostMapping("/protocol/{id}/result")
    public String saveResult(@PathVariable Long id,
                             @RequestParam String result,
                             RedirectAttributes flash){
        medicalCardService.updateProtocol(id, result);
        flash.addFlashAttribute("success", "Protocol updated successfully");
        return "redirect:/medical/protocol/" + id;
    }

    @PostMapping("/protocol/{appointmentId}/note")
    public String addNote(@PathVariable Long appointmentId,
                          @RequestParam String content,
                          @AuthenticationPrincipal UserDetails currentUser,
                          RedirectAttributes flash){
        var userStaff = staffRepository.findAll().stream()
                .filter(s -> s.getUser().getLogin().equals(currentUser.getUsername()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Staff not found for user " + currentUser.getUsername()));
        medicalCardService.addNote(appointmentId, userStaff.getId(), content);
        flash.addFlashAttribute("success", "Note added successfully");
        var apt = appointmentService.findById(appointmentId);
        return "redirect:/medical/patient/" + apt.getPatient().getId();
    }

    @PostMapping("/protocol/{id}/diagnosis")
    public String addDiagnosis(@PathVariable Long id,
                               @RequestParam Long diagnosisId,
                               @RequestParam(defaultValue = "false") boolean isMain,
                               RedirectAttributes flash) {
        medicalCardService.addDiagnosis(id, diagnosisId, isMain);
        flash.addFlashAttribute("success", "Діагноз додано.");
        return "redirect:/medical/protocol/" + id;
    }

    @PostMapping("/protocol/{id}/diagnosis/{dpId}/delete")
    public String removeDiagnosis(@PathVariable Long id,
                                  @PathVariable Long dpId,
                                  RedirectAttributes flash) {
        medicalCardService.removeDiagnosis(dpId);
        flash.addFlashAttribute("success", "Діагноз видалено.");
        return "redirect:/medical/protocol/" + id;
    }

    @PostMapping("/protocol/{id}/therapy")
    public String addTherapy(@PathVariable Long id,
                             @RequestParam Long therapyId,
                             @RequestParam(required = false) String dosage,
                             @RequestParam(required = false) String frequency,
                             @RequestParam(required = false) String instructions,
                             RedirectAttributes flash) {
        medicalCardService.addTherapy(id, therapyId, dosage, frequency, instructions);
        flash.addFlashAttribute("success", "Призначення додано.");
        return "redirect:/medical/protocol/" + id;
    }

    @PostMapping("/protocol/{id}/therapy/{tpId}/delete")
    public String removeTherapy(@PathVariable Long id,
                                @PathVariable Long tpId,
                                RedirectAttributes flash) {
        medicalCardService.removeTherapy(tpId);
        flash.addFlashAttribute("success", "Призначення видалено.");
        return "redirect:/medical/protocol/" + id;
    }
}